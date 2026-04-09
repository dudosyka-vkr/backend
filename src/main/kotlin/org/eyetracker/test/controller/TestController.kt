package org.eyetracker.test.controller

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.test.dto.ErrorResponse
import org.eyetracker.test.dto.MoveImageRequest
import org.eyetracker.test.dto.UpdateRoisRequest
import org.eyetracker.test.service.ImageResult
import org.eyetracker.test.service.RoiStatsResult
import org.eyetracker.test.service.TestResult
import org.eyetracker.test.service.TestService
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream
import java.io.InputStream

private fun RoutingCall.userId(): Int =
    principal<JWTPrincipal>()!!.payload.getClaim("userId").asInt()

private fun RoutingCall.role(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("role").asString()

private suspend fun RoutingCall.requireAdmin(): Boolean {
    val r = role()
    if (r != "ADMIN" && r != "SUPER_ADMIN") {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Admin access required"))
        return false
    }
    return true
}

fun Route.testRoutes(testService: TestService) {
    get("/tests/by-token/{code}") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Code is required"))

        when (val result = testService.getTestByToken(code)) {
            is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
            is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
        }
    }

    authenticate("auth-jwt") {
        route("/tests") {

            // 1. Create test (title + cover)
            post {
                if (!call.requireAdmin()) return@post

                val userId = call.userId()
                val multipart = call.receiveMultipart()
                var name: String? = null
                var coverBytes: ByteArray? = null
                var coverExtension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "name") name = part.value
                        }
                        is PartData.FileItem -> {
                            if (part.name == "cover") {
                                coverExtension = part.originalFileName
                                    ?.substringAfterLast('.', "jpg")
                                    ?.lowercase() ?: "jpg"
                                coverBytes = part.provider().toByteArray()
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (name == null || coverBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Name and cover are required"))
                    return@post
                }

                val coverStream: InputStream = ByteArrayInputStream(coverBytes!!)

                when (val result = testService.create(name!!, coverStream, coverExtension, userId)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            // Update test name
            patch("/{id}/name") {
                if (!call.requireAdmin()) return@patch

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val params = call.receiveParameters()
                val name = params["name"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Name is required"))

                when (val result = testService.updateName(testId, name)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            // Update test cover
            patch("/{id}/cover") {
                if (!call.requireAdmin()) return@patch

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val multipart = call.receiveMultipart()
                var coverBytes: ByteArray? = null
                var coverExtension = "jpg"

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem && part.name == "cover") {
                        coverExtension = part.originalFileName
                            ?.substringAfterLast('.', "jpg")
                            ?.lowercase() ?: "jpg"
                        coverBytes = part.provider().toByteArray()
                    }
                    part.dispose()
                }

                if (coverBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cover file is required"))
                    return@patch
                }

                val coverStream: InputStream = ByteArrayInputStream(coverBytes!!)

                when (val result = testService.updateCover(testId, coverStream, coverExtension)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            // 2. Add image (appends to end)
            post("/{id}/images") {
                if (!call.requireAdmin()) return@post

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val multipart = call.receiveMultipart()
                var imageBytes: ByteArray? = null
                var imageExtension = "jpg"

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem && part.name == "image") {
                        imageExtension = part.originalFileName
                            ?.substringAfterLast('.', "jpg")
                            ?.lowercase() ?: "jpg"
                        imageBytes = part.provider().toByteArray()
                    }
                    part.dispose()
                }

                if (imageBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Image file is required"))
                    return@post
                }

                val imageStream: InputStream = ByteArrayInputStream(imageBytes!!)

                when (val result = testService.addImage(testId, imageStream, imageExtension)) {
                    is ImageResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                    is ImageResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            // 3. Move image to new position
            patch("/images/{id}/position") {
                if (!call.requireAdmin()) return@patch

                val imageId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image ID"))

                val body = call.receive<MoveImageRequest>()
                val result = testService.reorderImage(imageId, body.newPosition)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Image not found"))

                call.respond(HttpStatusCode.OK, result)
            }

            // 4. Delete image
            delete("/images/{id}") {
                if (!call.requireAdmin()) return@delete

                val imageId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image ID"))

                val error = testService.deleteImage(imageId)
                if (error != null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error))
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // 5. Update ROI (array of regions)
            patch("/images/{id}/roi") {
                if (!call.requireAdmin()) return@patch

                val imageId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image ID"))

                val body = call.receive<UpdateRoisRequest>()
                val result = testService.updateImageRois(imageId, body.rois)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Image not found"))

                call.respond(HttpStatusCode.OK, result)
            }

            // Sync ROI metrics across records
            post("/{id}/sync-roi") {
                if (!call.requireAdmin()) return@post

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = testService.syncRoiMetrics(testId)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            // Generate or retrieve pass token for a test
            post("/{id}/token") {
                if (!call.requireAdmin()) return@post

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val result = testService.getOrCreateToken(testId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Test not found"))

                call.respond(HttpStatusCode.OK, result)
            }

            // Read endpoints
            get {
                call.respond(testService.getAll())
            }

            get("/{id}") {
                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = testService.getById(testId)) {
                    is TestResult.Success -> call.respond(result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            delete("/{id}") {
                if (!call.requireAdmin()) return@delete

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = testService.delete(testId)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            get("/{id}/roi-stats") {
                if (!call.requireAdmin()) return@get

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = testService.getRoiStats(testId, call.userId())) {
                    is RoiStatsResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is RoiStatsResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            get("/{id}/cover") {
                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val file = testService.getCoverFile(testId)
                if (file == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Test not found"))
                } else {
                    call.respondFile(file)
                }
            }

            get("/{id}/images/{index}") {
                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))
                val index = call.parameters["index"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image index"))

                val file = testService.getImageFile(testId, index)
                if (file == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Image not found"))
                } else {
                    call.respondFile(file)
                }
            }

            // Bulk update (kept for compatibility)
            put("/{id}") {
                if (!call.requireAdmin()) return@put

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val multipart = call.receiveMultipart()
                var name: String? = null
                var coverBytes: ByteArray? = null
                var coverExtension = "jpg"
                val imageEntries = mutableListOf<Pair<ByteArray, String>>()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "name") name = part.value
                        }
                        is PartData.FileItem -> {
                            val ext = part.originalFileName
                                ?.substringAfterLast('.', "jpg")
                                ?.lowercase() ?: "jpg"
                            val bytes = part.provider().toByteArray()
                            when (part.name) {
                                "cover" -> {
                                    coverBytes = bytes
                                    coverExtension = ext
                                }
                                "images" -> {
                                    imageEntries.add(bytes to ext)
                                }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (name == null || coverBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Name and cover are required"))
                    return@put
                }

                val coverStream: InputStream = ByteArrayInputStream(coverBytes!!)
                val imageStreams = imageEntries.map { (bytes, ext) -> ByteArrayInputStream(bytes) as InputStream to ext }

                when (val result = testService.update(testId, name!!, coverStream, coverExtension, imageStreams)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }
        }
    }
}
