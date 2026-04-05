package org.eyetracker.test.dto

import kotlinx.serialization.Serializable

@Serializable
data class TestResponse(
    val id: Int,
    val name: String,
    val coverUrl: String,
    val imageUrls: List<String>,
    val imageIds: List<Int>,
    val fixationTrackingAreas: List<String?>,
    val createdAt: String,
)

@Serializable
data class TestListResponse(
    val tests: List<TestResponse>,
)

@Serializable
data class UpdateFixationAreaRequest(val fixationTrackingArea: String)

@Serializable
data class UpdateFixationAreaResponse(val imageId: Int, val fixationTrackingArea: String)

@Serializable
data class ErrorResponse(val error: String)
