package org.eyetracker.test.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class UpdateAoiRequest(val aoi: List<JsonObject>)

@Serializable
data class UpdateAoiResponse(val testId: Int, val aoi: List<JsonObject>)

@Serializable
data class TestPassTokenResponse(
    val code: String,
    val testId: Int,
)

@Serializable
data class AoiStatEntry(
    val name: String,
    val color: String,
    val hits: Int,
    val total: Int,
    val firstFixationRequired: Boolean,
)

@Serializable
data class AoiStatsResponse(
    val aois: List<AoiStatEntry>,
    val totalRecords: Int,
    val uniqueUsers: Int,
)

@Serializable
data class TestResponse(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val aoi: List<JsonObject>,
    val createdAt: String,
)

@Serializable
data class TestListResponse(
    val tests: List<TestResponse>,
)
