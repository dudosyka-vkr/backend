package org.eyetracker.test.controller

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.eyetracker.test.dto.ErrorResponse
import org.eyetracker.test.dto.UpdateAoiRequest
import org.eyetracker.test.service.AoiStatsResult
import org.eyetracker.test.service.TestResult
import org.eyetracker.test.service.TestService
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

fun Route.testRoutes(service: TestService) {
    get("/tests/by-token/{code}") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Code is required"))

        when (val result = service.getByToken(code)) {
            is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
            is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
        }
    }

    authenticate("auth-jwt") {
        route("/tests") {

            post {
                if (!call.requireAdmin()) return@post

                val userId = call.userId()
                val multipart = call.receiveMultipart()
                var name: String? = null
                var imageBytes: ByteArray? = null
                var imageExtension = "jpg"

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "name") name = part.value
                        }
                        is PartData.FileItem -> {
                            if (part.name == "image") {
                                imageExtension = part.originalFileName
                                    ?.substringAfterLast('.', "jpg")
                                    ?.lowercase() ?: "jpg"
                                imageBytes = part.provider().toByteArray()
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (name == null || imageBytes == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Name and image are required"))
                    return@post
                }

                val imageStream: InputStream = ByteArrayInputStream(imageBytes!!)

                when (val result = service.create(name!!, userId, imageStream, imageExtension)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            patch("/{id}/name") {
                if (!call.requireAdmin()) return@patch

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val params = call.receiveParameters()
                val name = params["name"]
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Name is required"))

                when (val result = service.updateName(testId, name)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            delete("/{id}") {
                if (!call.requireAdmin()) return@delete

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = service.delete(testId)) {
                    is TestResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            post("/{id}/token") {
                if (!call.requireAdmin()) return@post

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val result = service.getOrCreateToken(testId)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("Test not found"))

                call.respond(HttpStatusCode.OK, result)
            }

            get("/{id}/aoi-stats") {
                if (!call.requireAdmin()) return@get

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = service.getAoiStats(testId, call.userId())) {
                    is AoiStatsResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is AoiStatsResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            patch("/{id}/aoi") {
                if (!call.requireAdmin()) return@patch

                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val body = call.receive<UpdateAoiRequest>()
                val result = service.updateAoi(testId, body.aoi)
                    ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("Test not found"))

                call.respond(HttpStatusCode.OK, result)
            }

            get {
                val nameFilter = call.request.queryParameters["name"]
                call.respond(service.getAll(call.userId(), nameFilter))
            }

            get("/{id}") {
                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                when (val result = service.getById(testId, call.userId())) {
                    is TestResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is TestResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            get("/{id}/image") {
                val testId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid test ID"))

                val file = service.getImageFile(testId)
                if (file == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Image not found"))
                } else {
                    call.respondFile(file)
                }
            }
        }
    }
}
