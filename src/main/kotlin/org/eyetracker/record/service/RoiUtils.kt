package org.eyetracker.record.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.log2

data class RoiComputeResult(
    val roiMetrics: List<JsonObject>,
    val aoiSequence: List<String?>,
)

/**
 * Ray-casting point-in-polygon test.
 * points is a JsonArray of {"x": Double, "y": Double} objects (normalised 0..1 coords).
 */
fun pointInPolygon(px: Double, py: Double, points: JsonArray): Boolean {
    val n = points.size
    if (n < 3) return false
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val pi = points[i].jsonObject
        val pj = points[j].jsonObject
        val xi = pi["x"]?.jsonPrimitive?.doubleOrNull
        val yi = pi["y"]?.jsonPrimitive?.doubleOrNull
        val xj = pj["x"]?.jsonPrimitive?.doubleOrNull
        val yj = pj["y"]?.jsonPrimitive?.doubleOrNull
        if (xi != null && yi != null && xj != null && yj != null) {
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside
            }
        }
        j = i
    }
    return inside
}

/**
 * Compute roi_metrics and aoi_sequence for one record.
 *
 * @param roisJson   JSON string — array of AOI objects.
 *                   Format: [{"name":str,"color":str,"first_fixation":bool,"points":[{x,y},...]}]
 * @param fixations  List of fixation objects from RecordItemMetrics.fixations.
 *                   Each fixation: {"center":{"x":Double,"y":Double},"is_first":bool,"start_ms":Long,...}
 * @return           RoiComputeResult with:
 *                   - roiMetrics: per-AOI list with hit, aoi_first_fixation, revisits
 *                   - aoiSequence: one entry per fixation — first matching AOI name or null
 */
fun computeRoiMetrics(roisJson: String?, fixations: List<JsonObject>): RoiComputeResult {
    if (roisJson.isNullOrBlank()) return RoiComputeResult(emptyList(), emptyList())

    val rois: List<JsonObject> = try {
        kotlinx.serialization.json.Json.parseToJsonElement(roisJson).jsonArray.map { it.jsonObject }
    } catch (_: Exception) {
        return RoiComputeResult(emptyList(), emptyList())
    }

    // Pre-parse polygon points once per AOI
    data class AoiDef(val name: String, val color: String, val firstRequired: Boolean, val points: JsonArray)
    val aoiDefs = rois.mapNotNull { roi ->
        val points = roi["points"]?.jsonArray ?: return@mapNotNull null
        val name = roi["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val color = roi["color"]?.jsonPrimitive?.content ?: "#00dc64"
        val firstRequired = roi["first_fixation"]?.jsonPrimitive?.booleanOrNull ?: false
        AoiDef(name, color, firstRequired, points)
    }

    // Pass over fixations: build aoi_sequence and collect per-AOI first fixation
    val aoiSequence = mutableListOf<String?>()
    val firstFixationInAoi = mutableMapOf<String, JsonObject>()

    for (fix in fixations) {
        val center = fix["center"]?.jsonObject
        val cx = center?.get("x")?.jsonPrimitive?.doubleOrNull
        val cy = center?.get("y")?.jsonPrimitive?.doubleOrNull

        var matched: String? = null
        if (cx != null && cy != null) {
            for (aoi in aoiDefs) {
                if (pointInPolygon(cx, cy, aoi.points)) {
                    matched = aoi.name
                    firstFixationInAoi.putIfAbsent(aoi.name, fix)
                    break
                }
            }
        }
        aoiSequence.add(matched)
    }

    // Run-length compress aoi_sequence, then count revisits per AOI
    val compressed = mutableListOf<String?>()
    for (name in aoiSequence) {
        if (compressed.isEmpty() || compressed.last() != name) compressed.add(name)
    }

    val revisits = mutableMapOf<String, Int>()
    val seen = mutableSetOf<String>()
    for (name in compressed) {
        if (name == null) continue
        if (name in seen) revisits[name] = (revisits[name] ?: 0) + 1
        else { seen.add(name); revisits[name] = 0 }
    }

    // hit logic still respects first_fixation_required flag
    val isFirstFix = fixations.firstOrNull { it["is_first"]?.jsonPrimitive?.booleanOrNull == true }

    val roiMetrics = aoiDefs.map { aoi ->
        val hit = if (aoi.firstRequired) {
            if (isFirstFix == null) false
            else {
                val center = isFirstFix["center"]?.jsonObject
                val cx = center?.get("x")?.jsonPrimitive?.doubleOrNull
                val cy = center?.get("y")?.jsonPrimitive?.doubleOrNull
                if (cx != null && cy != null) pointInPolygon(cx, cy, aoi.points) else false
            }
        } else {
            aoi.name in firstFixationInAoi
        }

        val aoiFirstFixationMs = firstFixationInAoi[aoi.name]?.get("start_ms")?.jsonPrimitive?.longOrNull

        buildJsonObject {
            put("name", aoi.name)
            put("color", aoi.color)
            put("hit", hit)
            put("firstFixationRequired", aoi.firstRequired)
            put("aoi_first_fixation", aoiFirstFixationMs?.let { JsonPrimitive(it) } ?: JsonNull)
            put("revisits", revisits[aoi.name] ?: 0)
        }
    }

    return RoiComputeResult(roiMetrics, aoiSequence)
}

fun computeTge(aoiSequence: List<String?>): Double? {
    val dwell = mutableMapOf<String, Int>()
    for (label in aoiSequence) {
        if (label != null) dwell[label] = (dwell[label] ?: 0) + 1
    }
    if (dwell.isEmpty()) return null
    val totalDwell = dwell.values.sum().toDouble()

    val compressed = mutableListOf<String?>()
    for (label in aoiSequence) {
        if (compressed.isEmpty() || compressed.last() != label) compressed.add(label)
    }

    val transitions = mutableMapOf<String, MutableMap<String, Int>>()
    var last: String? = null
    for (label in compressed) {
        if (label == null) continue
        if (last != null) transitions.getOrPut(last) { mutableMapOf() }.merge(label, 1, Int::plus)
        last = label
    }
    if (transitions.isEmpty()) return 0.0

    var tge = 0.0
    for ((aoiI, countI) in dwell) {
        val pi = countI / totalDwell
        val row = transitions[aoiI] ?: continue
        val totalFromI = row.values.sum().toDouble()
        tge += pi * row.values.sumOf { c -> val p = c / totalFromI; -p * log2(p) }
    }
    return tge
}
