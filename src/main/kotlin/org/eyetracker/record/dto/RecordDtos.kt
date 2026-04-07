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
data class CreateRecordRequest(
    val testId: Int,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val items: List<CreateRecordItemRequest>,
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
data class ErrorResponse(val error: String)
