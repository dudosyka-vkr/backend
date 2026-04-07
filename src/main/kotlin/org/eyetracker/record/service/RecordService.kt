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
import org.eyetracker.record.dao.RecordWithItems
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.RecordDetailResponse
import org.eyetracker.record.dto.RecordItemResponse
import org.eyetracker.record.dto.RecordListResponse
import org.eyetracker.record.dto.RecordSummaryResponse
import org.eyetracker.record.dto.UserSuggestResponse
import org.eyetracker.test.dao.TestDao

sealed class RecordResult {
    data class Success(val response: RecordDetailResponse) : RecordResult()
    data class Error(val message: String, val status: Int) : RecordResult()
}

class RecordService(
    private val recordDao: RecordDao,
    private val testDao: TestDao,
) {
    fun create(request: CreateRecordRequest, userLogin: String): RecordResult {
        if (request.items.isEmpty()) {
            return RecordResult.Error("At least one item is required", 400)
        }
        if (request.durationMs < 0) {
            return RecordResult.Error("Duration must be non-negative", 400)
        }

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
            request.testId, userLogin, startedAt, finishedAt, request.durationMs, items,
        )

        return RecordResult.Success(toDetailResponse(result))
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
            return RecordListResponse(
                items = pageRecords.map { toSummaryResponse(it) },
                page = clampedPage,
                pageSize = clampedPageSize,
                total = total,
            )
        }

        val (records, total) = recordDao.findAll(clampedPage, clampedPageSize, testId, userLogin, userLoginContains, fromInstant, toInstant)
        return RecordListResponse(
            items = records.map { toSummaryResponse(it) },
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

    private fun toSummaryResponse(record: RecordEntity): RecordSummaryResponse {
        return RecordSummaryResponse(
            id = record.id.value,
            testId = record.testId,
            userLogin = record.userLogin,
            startedAt = record.startedAt.toString(),
            finishedAt = record.finishedAt.toString(),
            durationMs = record.durationMs,
            createdAt = record.createdAt.toString(),
        )
    }
}
