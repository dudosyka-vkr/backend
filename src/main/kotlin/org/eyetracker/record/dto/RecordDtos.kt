package org.eyetracker.record.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class RecordItemMetrics(
    val gazeGroups: List<JsonObject> = emptyList(),
    val fixations: List<JsonObject> = emptyList(),
    val firstFixationTimeMs: Int? = null,
    val saccades: List<JsonObject> = emptyList(),
    val roiMetrics: List<JsonObject> = emptyList(),
)

@Serializable
data class CreateRecordItemRequest(
    val imageId: Int,
    val metrics: RecordItemMetrics,
)

@Serializable
data class CreateUnauthorizedRecordRequest(
    val token: String,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val items: List<CreateRecordItemRequest>,
    val login: String? = null,
)

@Serializable
data class CreateRecordRequest(
    val testId: Int,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val items: List<CreateRecordItemRequest>,
    val login: String? = null,
) {
    companion object {
        public fun of(request: CreateRecordRequest, login: String): CreateRecordRequest {
            return CreateRecordRequest(
                testId = request.testId,
                startedAt = request.startedAt,
                finishedAt = request.finishedAt,
                durationMs = request.durationMs,
                items = request.items,
                login = login,
            )
        }
    }
}

@Serializable
data class RoiHitEntry(
    val name: String,
    val hit: Boolean,
)

@Serializable
data class RecordSummaryResponse(
    val id: Int,
    val testId: Int,
    val userLogin: String,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val createdAt: String,
    val roiHits: List<RoiHitEntry>,
)

@Serializable
data class RecordItemResponse(
    val id: Int,
    val imageId: Int,
    val metrics: RecordItemMetrics,
)

@Serializable
data class RecordDetailResponse(
    val id: Int,
    val testId: Int,
    val userLogin: String,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val createdAt: String,
    val items: List<RecordItemResponse>,
)

@Serializable
data class RecordListResponse(
    val items: List<RecordSummaryResponse>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class UserSuggestResponse(
    val items: List<String>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class RoiSyncResponse(
    val synced: Boolean,
    val totalItems: Int,
    val outOfSyncCount: Int,
)

@Serializable
data class ErrorResponse(val error: String)
