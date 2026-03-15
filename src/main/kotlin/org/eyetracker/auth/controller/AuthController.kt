package org.eyetracker.auth.controller

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.auth.dto.ErrorResponse
import org.eyetracker.auth.dto.LoginRequest
import org.eyetracker.auth.dto.RegisterRequest
import org.eyetracker.auth.dto.TokenResponse
import org.eyetracker.auth.service.AuthResult
import org.eyetracker.auth.service.AuthService

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            when (val result = authService.login(request.login, request.password)) {
                is AuthResult.Success -> call.respond(TokenResponse(result.token))
                is AuthResult.Error -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
            }
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()
            when (val result = authService.register(request.login, request.password)) {
                is AuthResult.Success -> call.respond(HttpStatusCode.Created, TokenResponse(result.token))
                is AuthResult.Error -> call.respond(HttpStatusCode.Conflict, ErrorResponse(result.message))
            }
        }
    }
}
