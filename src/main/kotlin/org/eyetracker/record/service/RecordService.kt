package org.eyetracker.record.service

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dao.RecordEntity
import org.eyetracker.record.dto.AoiSyncResponse
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.CreateUnauthorizedRecordRequest
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.record.dto.RecordListItem
import org.eyetracker.record.dto.RecordListResponse
import org.eyetracker.record.dto.RecordResponse
import org.eyetracker.record.dto.UserSuggestResponse
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestPassTokenDao

private val lenientJson = Json { ignoreUnknownKeys = true }

sealed class RecordResult {
    data class Success(val response: RecordResponse) : RecordResult()
    data class Error(val message: String, val status: Int) : RecordResult()
}

class RecordService(
    private val dao: RecordDao,
    private val testDao: TestDao,
    private val tokenDao: TestPassTokenDao,
) {
    fun create(request: CreateRecordRequest, userLogin: String): RecordResult {
        if (request.durationMs < 0) return RecordResult.Error("Duration must be non-negative", 400)

        testDao.findById(request.testId) ?: return RecordResult.Error("Test not found", 404)

        val startedAt: Instant
        val finishedAt: Instant
        try {
            startedAt = Instant.parse(request.startedAt)
            finishedAt = Instant.parse(request.finishedAt)
        } catch (_: IllegalArgumentException) {
            return RecordResult.Error("Invalid timestamp format", 400)
        }

        val metricsJson = Json.encodeToString(request.metrics)
        val entity = dao.create(request.testId, userLogin, startedAt, finishedAt, request.durationMs, metricsJson)
        return RecordResult.Success(toResponse(entity))
    }

    fun createByToken(request: CreateUnauthorizedRecordRequest): RecordResult {
        val token = tokenDao.findByCode(request.token) ?: return RecordResult.Error("Invalid token", 404)
        val login = request.login ?: return RecordResult.Error("Login is required", 400)
        return create(
            CreateRecordRequest(
                testId = token.testId,
                startedAt = request.startedAt,
                finishedAt = request.finishedAt,
                durationMs = request.durationMs,
                metrics = request.metrics,
            ),
            login,
        )
    }

    fun getById(id: Int): RecordResult {
        val entity = dao.findById(id) ?: return RecordResult.Error("Record not found", 404)
        return RecordResult.Success(toResponse(entity))
    }

    fun getAll(
        page: Int,
        pageSize: Int,
        testId: Int?,
        userLogin: String?,
        userLoginContains: String?,
        from: String?,
        to: String?,
        aoiFilter: Map<String, Boolean> = emptyMap(),
    ): RecordListResponse {
        val clampedPage = maxOf(page, 1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val fromInstant = from?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val toInstant = to?.let { runCatching { Instant.parse(it) }.getOrNull() }

        if (aoiFilter.isNotEmpty()) {
            val all = dao.findAllUnpaginated(testId, userLogin, userLoginContains, fromInstant, toInstant)
            val filtered = all.filter { metricsMatchesAoiFilter(it.metricsJson, aoiFilter) }
            val total = filtered.size
            val page_ = filtered.drop((clampedPage - 1) * clampedPageSize).take(clampedPageSize)
            return RecordListResponse(page_.map { toListItem(it) }, clampedPage, clampedPageSize, total)
        }

        val (records, total) = dao.findAll(clampedPage, clampedPageSize, testId, userLogin, userLoginContains, fromInstant, toInstant)
        return RecordListResponse(records.map { toListItem(it) }, clampedPage, clampedPageSize, total.toInt())
    }

    fun suggestUsers(
        page: Int,
        pageSize: Int,
        testId: Int?,
        from: String?,
        to: String?,
    ): UserSuggestResponse {
        val clampedPage = maxOf(page, 1)
        val clampedPageSize = pageSize.coerceIn(1, 100)
        val fromInstant = from?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val toInstant = to?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val (logins, total) = dao.suggestUsers(clampedPage, clampedPageSize, testId, fromInstant, toInstant)
        return UserSuggestResponse(logins, clampedPage, clampedPageSize, total.toInt())
    }

    fun syncAoiMetrics(testId: Int): RecordResult {
        val test = testDao.findById(testId) ?: return RecordResult.Error("Test not found", 404)
        val records = dao.findAllForTest(testId)
        for (record in records) {
            val metrics = runCatching {
                lenientJson.decodeFromString<RecordItemMetrics>(record.metricsJson)
            }.getOrDefault(RecordItemMetrics())
            val newRoiMetrics = computeRoiMetrics(test.aoi, metrics.fixations)
            val updated = metrics.copy(roiMetrics = newRoiMetrics)
            dao.updateMetrics(record.id.value, Json.encodeToString(updated))
        }
        return RecordResult.Success(RecordResponse(0, testId, "", "", "", 0, "", RecordItemMetrics()))
    }

    fun checkAoiSync(testId: Int): AoiSyncResponse? {
        val test = testDao.findById(testId) ?: return null
        val aoiNames: Set<String> = test.aoi?.let { json ->
            runCatching {
                Json.parseToJsonElement(json).jsonArray
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                    .toSet()
            }.getOrDefault(emptySet())
        } ?: emptySet()

        val records = dao.findAllForTest(testId)
        var outOfSyncCount = 0
        for (record in records) {
            if (aoiNames.isEmpty()) continue
            val metrics = runCatching {
                lenientJson.decodeFromString<RecordItemMetrics>(record.metricsJson)
            }.getOrDefault(RecordItemMetrics())
            val present = metrics.roiMetrics
                .mapNotNull { it["name"]?.jsonPrimitive?.content }
                .toSet()
            if (present != aoiNames) outOfSyncCount++
        }

        return AoiSyncResponse(
            synced = outOfSyncCount == 0,
            totalRecords = records.size,
            outOfSyncCount = outOfSyncCount,
        )
    }

    private fun metricsMatchesAoiFilter(metricsJson: String, filter: Map<String, Boolean>): Boolean {
        return try {
            val obj = Json.parseToJsonElement(metricsJson).jsonObject
            val roiMetrics = obj["roiMetrics"]?.jsonArray ?: return filter.all { (_, required) -> !required }
            val hitMap = mutableMapOf<String, Boolean>()
            for (roi in roiMetrics) {
                val roiObj = roi.jsonObject
                val name = roiObj["name"]?.jsonPrimitive?.content ?: continue
                val hit = roiObj["hit"]?.jsonPrimitive?.booleanOrNull ?: false
                hitMap[name] = (hitMap[name] ?: false) || hit
            }
            filter.all { (name, required) -> (hitMap[name] ?: false) == required }
        } catch (_: Exception) {
            false
        }
    }

    private fun toListItem(entity: RecordEntity): RecordListItem {
        val metrics = runCatching {
            lenientJson.decodeFromString<RecordItemMetrics>(entity.metricsJson)
        }.getOrDefault(RecordItemMetrics())
        val aoiHits = metrics.roiMetrics.mapNotNull { roi ->
            val hit = roi["hit"]?.jsonPrimitive?.booleanOrNull ?: false
            if (hit) roi["name"]?.jsonPrimitive?.content else null
        }
        return RecordListItem(
            id = entity.id.value,
            testId = entity.testId,
            userLogin = entity.userLogin,
            startedAt = entity.startedAt.toString(),
            finishedAt = entity.finishedAt.toString(),
            durationMs = entity.durationMs,
            createdAt = entity.createdAt.toString(),
            aoiHits = aoiHits,
        )
    }

    private fun toResponse(entity: RecordEntity): RecordResponse {
        val metrics = runCatching {
            lenientJson.decodeFromString<RecordItemMetrics>(entity.metricsJson)
        }.getOrDefault(RecordItemMetrics())
        return RecordResponse(
            id = entity.id.value,
            testId = entity.testId,
            userLogin = entity.userLogin,
            startedAt = entity.startedAt.toString(),
            finishedAt = entity.finishedAt.toString(),
            durationMs = entity.durationMs,
            createdAt = entity.createdAt.toString(),
            metrics = metrics,
        )
    }
}
