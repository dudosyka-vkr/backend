package org.eyetracker.record.controller

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.CreateUnauthorizedRecordRequest
import org.eyetracker.record.dto.ErrorResponse
import org.eyetracker.record.service.RecordResult
import org.eyetracker.record.service.RecordService

private fun RoutingCall.userLogin(): String =
    principal<JWTPrincipal>()!!.payload.getClaim("email").asString()

fun Route.recordRoutes(service: RecordService) {
    post("/records/unauthorized") {
        val request = call.receive<CreateUnauthorizedRecordRequest>()
        when (val result = service.createByToken(request)) {
            is RecordResult.Success -> call.respond(HttpStatusCode.Created, result.response)
            is RecordResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
        }
    }

    authenticate("auth-jwt") {
        route("/records") {

            post {
                val request = call.receive<CreateRecordRequest>()
                val userLogin = call.userLogin()
                when (val result = service.create(request, userLogin)) {
                    is RecordResult.Success -> call.respond(HttpStatusCode.Created, result.response)
                    is RecordResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
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
                val aoiFilter = call.request.queryParameters.entries()
                    .filter { it.key.startsWith("aoi.") }
                    .associate { it.key.removePrefix("aoi.") to (it.value.firstOrNull() == "true") }

                call.respond(service.getAll(page, pageSize, testId, userLogin, userLoginContains, from, to, aoiFilter))
            }

            get("/users/suggest") {
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val testId = call.request.queryParameters["testId"]?.toIntOrNull()
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]

                call.respond(service.suggestUsers(page, pageSize, testId, from, to))
            }

            get("/aoi-sync") {
                val testId = call.request.queryParameters["testId"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("testId is required"))

                val result = service.checkAoiSync(testId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Test not found"))

                call.respond(HttpStatusCode.OK, result)
            }

            post("/sync-aoi") {
                val testId = call.request.queryParameters["testId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("testId is required"))

                when (val result = service.syncAoiMetrics(testId)) {
                    is RecordResult.Success -> call.respond(HttpStatusCode.NoContent)
                    is RecordResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }

            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid record ID"))

                when (val result = service.getById(id)) {
                    is RecordResult.Success -> call.respond(HttpStatusCode.OK, result.response)
                    is RecordResult.Error -> call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }
        }
    }
}
