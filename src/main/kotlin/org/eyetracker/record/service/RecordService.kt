package org.eyetracker.record.service

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eyetracker.record.dao.CreateRecordItemData
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dao.RecordEntity
import org.eyetracker.record.dao.RecordItemBriefData
import org.eyetracker.record.dao.RecordWithItems
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.CreateUnauthorizedRecordRequest
import org.eyetracker.record.dto.RecordDetailResponse
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.record.dto.RecordItemResponse
import org.eyetracker.record.dto.RecordListResponse
import org.eyetracker.record.dto.RecordSummaryResponse
import org.eyetracker.record.dto.RoiHitEntry
import org.eyetracker.record.dto.RoiSyncResponse
import org.eyetracker.record.dto.UserSuggestResponse
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestPassTokenDao

private val lenientJson = Json { ignoreUnknownKeys = true }

sealed class RecordResult {
    data class Success(val response: RecordDetailResponse) : RecordResult()
    data class Error(val message: String, val status: Int) : RecordResult()
}

class RecordService(
    private val recordDao: RecordDao,
    private val testDao: TestDao,
    private val testPassTokenDao: TestPassTokenDao,
) {
    fun create(request: CreateRecordRequest): RecordResult {
        if (request.items.isEmpty()) {
            return RecordResult.Error("At least one item is required", 400)
        }
        if (request.durationMs < 0) {
            return RecordResult.Error("Duration must be non-negative", 400)
        }

        request.login ?: return RecordResult.Error("Login is required", 400)

        val startedAt: Instant
        val finishedAt: Instant
        try {
            startedAt = Instant.parse(request.startedAt)
            finishedAt = Instant.parse(request.finishedAt)
        } catch (e: IllegalArgumentException) {
            return RecordResult.Error("Invalid timestamp format", 400)
        }

        testDao.findById(request.testId)
            ?: return RecordResult.Error("Test not found", 404)

        val validImageIds = testDao.findImageIdsByTestId(request.testId).toSet()
        for (item in request.items) {
            if (item.imageId !in validImageIds) {
                return RecordResult.Error("Image ID ${item.imageId} does not belong to test ${request.testId}", 400)
            }
        }

        val items = request.items.map { CreateRecordItemData(it.imageId, it.metrics) }
        val result = recordDao.create(
            request.testId, request.login, startedAt, finishedAt, request.durationMs, items,
        )

        return RecordResult.Success(toDetailResponse(result))
    }

    fun createByToken(request: CreateUnauthorizedRecordRequest): RecordResult {
        val token = testPassTokenDao.findByCode(request.token)
            ?: return RecordResult.Error("Invalid token", 404)
        return create(
            CreateRecordRequest(
                testId = token.testId,
                startedAt = request.startedAt,
                finishedAt = request.finishedAt,
                durationMs = request.durationMs,
                items = request.items,
                login = request.login,
            )
        )
    }

    fun getById(recordId: Int): RecordResult {
        val result = recordDao.findById(recordId)
            ?: return RecordResult.Error("Record not found", 404)
        return RecordResult.Success(toDetailResponse(result))
    }

    fun getAll(
        page: Int,
        pageSize: Int,
        testId: Int?,
        userLogin: String?,
        userLoginContains: String?,
        from: String?,
        to: String?,
        roiFilter: Map<String, Boolean> = emptyMap(),
    ): RecordListResponse {
        val clampedPage = maxOf(page, 1)
        val clampedPageSize = pageSize.coerceIn(1, 100)

        val fromInstant = from?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val toInstant = to?.let { runCatching { Instant.parse(it) }.getOrNull() }

        if (roiFilter.isNotEmpty()) {
            val allRecords = recordDao.findAllUnpaginated(testId, userLogin, userLoginContains, fromInstant, toInstant)
            val metricsJsonByRecord = recordDao.findMetricsJsonForRecords(allRecords.map { it.id.value })
            val filtered = allRecords.filter { record ->
                metricsJsonByRecord[record.id.value].orEmpty().any { metricsJson ->
                    metricsMatchesRoiFilter(metricsJson, roiFilter)
                }
            }
            val total = filtered.size
            val pageRecords = filtered.drop((clampedPage - 1) * clampedPageSize).take(clampedPageSize)
            val itemsByRecord = recordDao.findItemsBriefForRecords(pageRecords.map { it.id.value })
            return RecordListResponse(
                items = pageRecords.map { toSummaryResponse(it, itemsByRecord[it.id.value] ?: emptyList()) },
                page = clampedPage,
                pageSize = clampedPageSize,
                total = total,
            )
        }

        val (records, total) = recordDao.findAll(clampedPage, clampedPageSize, testId, userLogin, userLoginContains, fromInstant, toInstant)
        val itemsByRecord = recordDao.findItemsBriefForRecords(records.map { it.id.value })
        return RecordListResponse(
            items = records.map { toSummaryResponse(it, itemsByRecord[it.id.value] ?: emptyList()) },
            page = clampedPage,
            pageSize = clampedPageSize,
            total = total.toInt(),
        )
    }

    fun suggestUsers(
        page: Int,
        pageSize: Int,
        testId: Int?,
        from: String?,
        to: String?,
        roiFilter: Map<String, Boolean> = emptyMap(),
    ): UserSuggestResponse {
        val clampedPage = maxOf(page, 1)
        val clampedPageSize = pageSize.coerceIn(1, 100)

        val fromInstant = from?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val toInstant = to?.let { runCatching { Instant.parse(it) }.getOrNull() }

        if (roiFilter.isNotEmpty()) {
            val allRecords = recordDao.findAllUnpaginated(testId, null, null, fromInstant, toInstant)
            val metricsJsonByRecord = recordDao.findMetricsJsonForRecords(allRecords.map { it.id.value })
            val filtered = allRecords.filter { record ->
                metricsJsonByRecord[record.id.value].orEmpty().any { metricsJson ->
                    metricsMatchesRoiFilter(metricsJson, roiFilter)
                }
            }
            val logins = filtered.map { it.userLogin }.distinct().sorted()
            val total = logins.size
            val pageLogins = logins.drop((clampedPage - 1) * clampedPageSize).take(clampedPageSize)
            return UserSuggestResponse(
                items = pageLogins,
                page = clampedPage,
                pageSize = clampedPageSize,
                total = total,
            )
        }

        val (logins, total) = recordDao.suggestUsers(clampedPage, clampedPageSize, testId, fromInstant, toInstant)
        return UserSuggestResponse(
            items = logins,
            page = clampedPage,
            pageSize = clampedPageSize,
            total = total.toInt(),
        )
    }

    fun checkRoiSync(testId: Int): RoiSyncResponse? {
        testDao.findById(testId) ?: return null

        val imageRois = testDao.findImageRoisByTestId(testId)
        val requiredNames: Map<Int, Set<String>> = imageRois.mapValues { (_, roiJson) ->
            roiJson?.let { parseRoiNames(it) } ?: emptySet()
        }

        val items = recordDao.findItemsForTest(testId)
        var outOfSyncCount = 0

        for (item in items) {
            val required = requiredNames[item.imageId] ?: emptySet()
            if (required.isEmpty()) continue
            val metrics = try {
                lenientJson.decodeFromString<RecordItemMetrics>(item.metricsJson)
            } catch (_: Exception) {
                RecordItemMetrics()
            }
            val present = metrics.roiMetrics
                .mapNotNull { it["name"]?.jsonPrimitive?.content }
                .toSet()
            if (present != required) outOfSyncCount++
        }

        return RoiSyncResponse(
            synced = outOfSyncCount == 0,
            totalItems = items.size,
            outOfSyncCount = outOfSyncCount,
        )
    }

    private fun parseRoiNames(roiJson: String): Set<String> = try {
        Json.parseToJsonElement(roiJson).jsonArray
            .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    private fun metricsMatchesRoiFilter(metricsJson: String, filter: Map<String, Boolean>): Boolean {
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
        } catch (e: Exception) {
            false
        }
    }

    private fun toDetailResponse(rwi: RecordWithItems): RecordDetailResponse {
        return RecordDetailResponse(
            id = rwi.record.id.value,
            testId = rwi.record.testId,
            userLogin = rwi.record.userLogin,
            startedAt = rwi.record.startedAt.toString(),
            finishedAt = rwi.record.finishedAt.toString(),
            durationMs = rwi.record.durationMs,
            createdAt = rwi.record.createdAt.toString(),
            items = rwi.items.map { RecordItemResponse(it.id, it.imageId, it.metrics) },
        )
    }

    private fun toSummaryResponse(record: RecordEntity, items: List<RecordItemBriefData>): RecordSummaryResponse {
        val roiHits = items.flatMap { item ->
            val metrics = runCatching {
                lenientJson.decodeFromString<RecordItemMetrics>(item.metricsJson)
            }.getOrDefault(RecordItemMetrics())
            metrics.roiMetrics.mapNotNull { roi ->
                val name = roi["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val hit = roi["hit"]?.jsonPrimitive?.booleanOrNull ?: false
                RoiHitEntry(name = "$name (${item.sortOrder})", hit = hit)
            }
        }
        return RecordSummaryResponse(
            id = record.id.value,
            testId = record.testId,
            userLogin = record.userLogin,
            startedAt = record.startedAt.toString(),
            finishedAt = record.finishedAt.toString(),
            durationMs = record.durationMs,
            createdAt = record.createdAt.toString(),
            roiHits = roiHits,
        )
    }
}
