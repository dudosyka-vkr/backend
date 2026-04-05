package org.eyetracker.test.dto

import kotlinx.serialization.Serializable

@Serializable
data class TestResponse(
    val id: Int,
    val name: String,
    val coverUrl: String,
    val imageUrls: List<String>,
    val imageIds: List<Int>,
    val rois: List<String?>,
    val createdAt: String,
)

@Serializable
data class TestListResponse(
    val tests: List<TestResponse>,
)

@Serializable
data class UpdateRoiRequest(val roi: String)

@Serializable
data class UpdateRoiResponse(val imageId: Int, val roi: String)

@Serializable
data class ErrorResponse(val error: String)
