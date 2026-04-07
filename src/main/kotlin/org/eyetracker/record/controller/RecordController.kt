package org.eyetracker.record.controller

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.ErrorResponse
import org.eyetracker.record.service.RecordResult
import org.eyetracker.record.service.RecordService

private fun RoutingCall.userLogin(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

fun Route.recordRoutes(recordService: RecordService) {
    authenticate("auth-jwt") {
        route("/records") {
            post {
                val request = call.receive<CreateRecordRequest>()
                val userLogin = call.userLogin()
                when (val result = recordService.create(request, userLogin)) {
                    is RecordResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                    is RecordResult.Error -> call.respond(
                        HttpStatusCode.fromValue(result.status), ErrorResponse(result.message),
                    )
                }
            }

            get {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val testId = call.request.queryParameters["testId"]?.toIntOrNull()
                val userLogin = call.request.queryParameters["userLogin"]
                val userLoginContains = call.request.queryParameters["userLoginContains"]
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]
                val roiFilter = call.request.queryParameters.entries()
                    .filter { it.key.startsWith("roi.") }
                    .associate { it.key.removePrefix("roi.") to (it.value.firstOrNull() == "true") }

                call.respond(recordService.getAll(page, pageSize, testId, userLogin, userLoginContains, from, to, roiFilter))
            }

            get("/users/suggest") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val testId = call.request.queryParameters["testId"]?.toIntOrNull()
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]
                val roiFilter = call.request.queryParameters.entries()
                    .filter { it.key.startsWith("roi.") }
                    .associate { it.key.removePrefix("roi.") to (it.value.firstOrNull() == "true") }

                call.respond(recordService.suggestUsers(page, pageSize, testId, from, to, roiFilter))
            }

            get("/{id}") {
                val recordId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID"))

                when (val result = recordService.getById(recordId)) {
                    is RecordResult.Success -> call.respond(result.response)
                    is RecordResult.Error -> call.respond(
                        HttpStatusCode.fromValue(result.status), ErrorResponse(result.message),
                    )
                }
            }
        }
    }
}
