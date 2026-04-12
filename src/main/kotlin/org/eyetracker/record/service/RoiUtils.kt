package org.eyetracker.record.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
 * Compute roi_metrics for one record item.
 *
 * @param roisJson   JSON string from test_images.roi — a JSON array of ROI objects.
 *                   Format: [{"name":str,"color":str,"first_fixation":bool,"points":[{x,y},...]}]
 * @param fixations  List of fixation objects from RecordItemMetrics.fixations.
 *                   Each fixation: {"center":{"x":Double,"y":Double},"is_first":bool,...}
 * @return           List of {"name":str,"color":str,"hit":bool,"firstFixationRequired":bool}
 */
fun computeRoiMetrics(roisJson: String?, fixations: List<JsonObject>): List<JsonObject> {
    if (roisJson.isNullOrBlank()) return emptyList()

    val rois: List<JsonObject> = try {
        kotlinx.serialization.json.Json.parseToJsonElement(roisJson).jsonArray
            .map { it.jsonObject }
    } catch (_: Exception) {
        return emptyList()
    }

    val firstFix = fixations.firstOrNull { fix ->
        fix["is_first"]?.jsonPrimitive?.booleanOrNull == true
    }

    return rois.mapNotNull { roi ->
        val points = roi["points"]?.jsonArray ?: return@mapNotNull null
        val name = roi["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val color = roi["color"]?.jsonPrimitive?.content ?: "#00dc64"
        val firstRequired = roi["first_fixation"]?.jsonPrimitive?.booleanOrNull ?: false

        val hit = if (firstRequired) {
            if (firstFix == null) {
                false
            } else {
                val center = firstFix["center"]?.jsonObject
                val cx = center?.get("x")?.jsonPrimitive?.doubleOrNull
                val cy = center?.get("y")?.jsonPrimitive?.doubleOrNull
                if (cx != null && cy != null) pointInPolygon(cx, cy, points) else false
            }
        } else {
            fixations.any { fix ->
                val center = fix["center"]?.jsonObject
                val cx = center?.get("x")?.jsonPrimitive?.doubleOrNull
                val cy = center?.get("y")?.jsonPrimitive?.doubleOrNull
                if (cx != null && cy != null) pointInPolygon(cx, cy, points) else false
            }
        }

        buildJsonObject {
            put("name", name)
            put("color", color)
            put("hit", hit)
            put("firstFixationRequired", firstRequired)
        }
    }
}
