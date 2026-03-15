package org.eyetracker.record.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecordItemMetrics(val placeholderMetric: Double = 0.0)

@Serializable
data class CreateRecordItemRequest(
    val imageId: Int,
    val metrics: RecordItemMetrics,
)

@Serializable
data class CreateRecordRequest(
    val testId: Int,
    val userLogin: String,
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
data class ErrorResponse(val error: String)
