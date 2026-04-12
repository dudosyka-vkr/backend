package org.eyetracker.record.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class RecordItemMetrics(
    val gazeGroups: List<JsonObject> = emptyList(),
    val fixations: List<JsonObject> = emptyList(),
    val firstFixationTimeMs: Int? = null,
    val saccades: List<JsonObject> = emptyList(),
    val roiMetrics: List<JsonObject> = emptyList(),
    val aoiSequence: List<String?> = emptyList(),
    val tge: Double? = null,
)

@Serializable
data class UserSuggestResponse(
    val items: List<String>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class CreateUnauthorizedRecordRequest(
    val token: String,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val metrics: RecordItemMetrics,
    val login: String? = null,
)

@Serializable
data class CreateRecordRequest(
    val testId: Int,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val metrics: RecordItemMetrics,
)

@Serializable
data class RecordResponse(
    val id: Int,
    val testId: Int,
    val userLogin: String,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val createdAt: String,
    val metrics: RecordItemMetrics,
)

@Serializable
data class RecordListItem(
    val id: Int,
    val testId: Int,
    val userLogin: String,
    val startedAt: String,
    val finishedAt: String,
    val durationMs: Long,
    val createdAt: String,
    val aoiHits: List<String>,
)

@Serializable
data class RecordListResponse(
    val items: List<RecordListItem>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
)

@Serializable
data class AoiSyncResponse(
    val synced: Boolean,
    val totalRecords: Int,
    val outOfSyncCount: Int,
)
