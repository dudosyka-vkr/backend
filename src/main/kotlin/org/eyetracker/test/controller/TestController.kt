package org.eyetracker.test.controller

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.test.dto.ErrorResponse
import org.eyetracker.test.dto.UpdateFixationAreaRequest
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
    authenticate("auth-jwt") {
        route("/tests") {
            post {
                if (!call.requireAdmin()) return@post

                val userId = call.userId()
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
                    return@post
                }

                val coverStream: InputStream = ByteArrayInputStream(coverBytes!!)
                val imageStreams = imageEntries.map { (bytes, ext) -> ByteArrayInputStream(bytes) as InputStream to ext }

                when (val result = testService.create(name!!, coverStream, coverExtension, imageStreams, userId)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

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

            patch("/images/{id}/fixation-area") {
                if (!call.requireAdmin()) return@patch

                val imageId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid image ID"))

                val body = call.receive<UpdateFixationAreaRequest>()
                val result = testService.updateImageFixationArea(imageId, body.fixationTrackingArea)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Image not found"))

                call.respond(HttpStatusCode.OK, result)
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
        }
    }
}
