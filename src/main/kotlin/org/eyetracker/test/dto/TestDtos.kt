package org.eyetracker.test.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TestImageInfo(
    val id: Int,
    val url: String,
    val sortOrder: Int,
    val rois: List<JsonObject>,
)

@Serializable
data class TestResponse(
    val id: Int,
    val name: String,
    val coverUrl: String,
    val createdAt: String,
    val images: List<TestImageInfo>,
)

@Serializable
data class TestListResponse(
    val tests: List<TestResponse>,
)

@Serializable
data class TestImageResponse(
    val imageId: Int,
    val imageUrl: String,
    val sortOrder: Int,
)

@Serializable
data class MoveImageRequest(val newPosition: Int)

@Serializable
data class MoveImageResponse(val imageId: Int, val sortOrder: Int)

@Serializable
data class UpdateRoisRequest(val rois: List<JsonObject>)

@Serializable
data class UpdateRoisResponse(val imageId: Int, val rois: List<JsonObject>)

@Serializable
data class RoiStatEntry(
    val name: String,
    val color: String,
    val hits: Int,
    val total: Int,
    val firstFixationRequired: Boolean,
)

@Serializable
data class RoiStatsResponse(
    val rois: List<RoiStatEntry>,
    val totalRecords: Int,
    val uniqueUsers: Int,
)

@Serializable
data class TestPassTokenResponse(
    val code: String,
    val testId: Int,
)

@Serializable
data class ErrorResponse(val error: String)
