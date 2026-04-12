package org.eyetracker.test.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestEntity
import org.eyetracker.test.dao.TestPassTokenDao
import org.eyetracker.test.dto.AoiStatEntry
import org.eyetracker.test.dto.AoiStatsResponse
import org.eyetracker.test.dto.HistogramBin
import org.eyetracker.test.dto.TgeHistogramBin
import org.eyetracker.test.dto.TestListResponse
import org.eyetracker.test.dto.TestPassTokenResponse
import org.eyetracker.test.dto.TestResponse
import org.eyetracker.test.dto.UpdateAoiResponse
import java.io.File
import java.io.InputStream

private val lenientJson = Json { ignoreUnknownKeys = true }

sealed class TestResult {
    data class Success(val response: TestResponse) : TestResult()
    data class Error(val message: String, val status: Int) : TestResult()
}

sealed class AoiStatsResult {
    data class Success(val response: AoiStatsResponse) : AoiStatsResult()
    data class Error(val message: String, val status: Int) : AoiStatsResult()
}

class TestService(
    private val dao: TestDao,
    private val tokenDao: TestPassTokenDao,
    private val recordDao: RecordDao,
    private val uploadDir: String,
) {
    fun create(
        name: String,
        userId: Int,
        imageStream: InputStream,
        imageExtension: String,
    ): TestResult {
        if (name.isBlank()) return TestResult.Error("Name is required", 400)

        val imageFilename = "image.$imageExtension"
        val entity = dao.create(name, imageFilename, userId)
        val testId = entity.id.value

        val dir = File(uploadDir, "tests/$testId")
        try {
            dir.mkdirs()
            imageStream.use { input ->
                File(dir, imageFilename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            dao.findById(testId)?.delete()
            return TestResult.Error("Failed to save image: ${e.message}", 500)
        }

        return TestResult.Success(toResponse(entity))
    }

    fun updateName(testId: Int, name: String): TestResult {
        if (name.isBlank()) return TestResult.Error("Name is required", 400)
        val updated = dao.updateName(testId, name)
        if (!updated) return TestResult.Error("Test not found", 404)
        return TestResult.Success(toResponse(dao.findById(testId)!!))
    }

    fun delete(testId: Int): TestResult {
        val entity = dao.findById(testId) ?: return TestResult.Error("Test not found", 404)
        val response = toResponse(entity)
        dao.deleteById(testId)
        File(uploadDir, "tests/$testId").deleteRecursively()
        return TestResult.Success(response)
    }

    fun getById(testId: Int, requestingUserId: Int): TestResult {
        val entity = dao.findById(testId) ?: return TestResult.Error("Test not found", 404)
        if (entity.userId != requestingUserId) return TestResult.Error("Access denied", 403)
        return TestResult.Success(toResponse(entity))
    }

    fun getAll(userId: Int, nameFilter: String?): TestListResponse {
        return TestListResponse(dao.findAll(userId, nameFilter).map { toResponse(it) })
    }

    fun updateAoi(testId: Int, aoi: List<JsonObject>): UpdateAoiResponse? {
        val updated = dao.updateAoi(testId, Json.encodeToString(aoi))
        if (!updated) return null
        return UpdateAoiResponse(testId, aoi)
    }

    fun getImageFile(testId: Int): File? {
        val entity = dao.findById(testId) ?: return null
        val file = File(uploadDir, "tests/$testId/${entity.image}")
        return if (file.exists()) file else null
    }

    fun getOrCreateToken(testId: Int): TestPassTokenResponse? {
        dao.findById(testId) ?: return null
        val existing = tokenDao.findByTestId(testId)
        if (existing != null) return TestPassTokenResponse(code = existing.code, testId = testId)
        var code: String
        do {
            code = (10_000_000..99_999_999).random().toString()
        } while (tokenDao.findByCode(code) != null)
        tokenDao.create(testId, code)
        return TestPassTokenResponse(code = code, testId = testId)
    }

    fun getByToken(code: String): TestResult {
        val token = tokenDao.findByCode(code) ?: return TestResult.Error("Token not found", 404)
        val entity = dao.findById(token.testId) ?: return TestResult.Error("Test not found", 404)
        return TestResult.Success(toResponse(entity))
    }

    fun getAoiStats(testId: Int, requestingUserId: Int): AoiStatsResult {
        val entity = dao.findById(testId) ?: return AoiStatsResult.Error("Test not found", 404)
        if (entity.userId != requestingUserId) return AoiStatsResult.Error("Access denied", 403)

        data class AoiDef(val color: String, val firstFixationRequired: Boolean)
        val aoiDefs = linkedMapOf<String, AoiDef>()
        entity.aoi?.let { json ->
            runCatching {
                Json.parseToJsonElement(json).jsonArray.forEach { el ->
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                    aoiDefs.putIfAbsent(
                        name,
                        AoiDef(
                            color = obj["color"]?.jsonPrimitive?.content ?: "",
                            firstFixationRequired = obj["first_fixation"]?.jsonPrimitive?.booleanOrNull ?: false,
                        ),
                    )
                }
            }
        }

        val records = recordDao.findAllForTest(testId)
        val hitsByRecord = mutableMapOf<Int, MutableSet<String>>()
        val userByRecord = mutableMapOf<Int, String>()
        val firstFixationMsByAoi = mutableMapOf<String, MutableList<Long>>()
        val tgeValues = mutableListOf<Double>()

        for (record in records) {
            userByRecord[record.id.value] = record.userLogin
            val metrics = runCatching {
                lenientJson.decodeFromString<RecordItemMetrics>(record.metricsJson)
            }.getOrDefault(RecordItemMetrics())
            metrics.tge?.let { tgeValues.add(it) }
            for (roiMetric in metrics.roiMetrics) {
                val name = roiMetric["name"]?.jsonPrimitive?.content ?: continue
                val hit = roiMetric["hit"]?.jsonPrimitive?.booleanOrNull ?: false
                if (hit) hitsByRecord.getOrPut(record.id.value) { mutableSetOf() }.add(name)
                val firstFixMs = roiMetric["aoi_first_fixation"]?.jsonPrimitive?.longOrNull
                if (firstFixMs != null) firstFixationMsByAoi.getOrPut(name) { mutableListOf() }.add(firstFixMs)
            }
        }

        val totalRecords = userByRecord.size
        val uniqueUsers = userByRecord.values.distinct().size
        val aois = aoiDefs.map { (name, def) ->
            AoiStatEntry(
                name = name,
                color = def.color,
                hits = hitsByRecord.values.count { name in it },
                total = totalRecords,
                firstFixationRequired = def.firstFixationRequired,
                firstFixationHistogram = buildHistogram(firstFixationMsByAoi[name] ?: emptyList()),
            )
        }

        return AoiStatsResult.Success(AoiStatsResponse(aois = aois, totalRecords = totalRecords, uniqueUsers = uniqueUsers, tgeHistogram = buildTgeHistogram(tgeValues)))
    }

    private fun buildTgeHistogram(values: List<Double>, binSize: Double = 0.1): List<TgeHistogramBin> {
        if (values.isEmpty()) return emptyList()
        val numBins = (values.max() / binSize).toInt() + 1
        val counts = IntArray(numBins)
        for (v in values) counts[(v / binSize).toInt()]++
        return counts.indices.map { i -> TgeHistogramBin(binStart = Math.round(i * binSize * 10) / 10.0, count = counts[i]) }
    }

    private fun buildHistogram(valuesMs: List<Long>, binSizeMs: Int = 500): List<HistogramBin> {
        if (valuesMs.isEmpty()) return emptyList()
        val numBins = (valuesMs.max() / binSizeMs + 1).toInt()
        val counts = IntArray(numBins)
        for (v in valuesMs) counts[(v / binSizeMs).toInt()]++
        return counts.indices.map { i -> HistogramBin(binStartMs = i * binSizeMs, count = counts[i]) }
    }

    private fun toResponse(entity: TestEntity): TestResponse {
        val aoi = entity.aoi?.let { json ->
            runCatching { Json.decodeFromString<List<JsonObject>>(json) }.getOrDefault(emptyList())
        } ?: emptyList()
        return TestResponse(
            id = entity.id.value,
            name = entity.name,
            imageUrl = "/tests/${entity.id.value}/image",
            aoi = aoi,
            createdAt = entity.createdAt.toString(),
        )
    }
}
