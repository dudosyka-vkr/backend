package org.eyetracker.auth.controller

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.auth.dto.*
import org.eyetracker.auth.service.AuthResult
import org.eyetracker.auth.service.AuthService
import org.eyetracker.auth.service.CreateUserResult
import org.eyetracker.auth.service.Role

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

        authenticate("auth-jwt") {
            post("/users") {
                val principal = call.principal<JWTPrincipal>()!!
                val callerRole = principal.payload.getClaim("role").asString()

                if (callerRole != Role.ADMIN && callerRole != Role.SUPER_ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Admin access required"))
                    return@post
                }

                val request = call.receive<CreateUserRequest>()
                when (val result = authService.createUser(request.login, request.password, request.role, callerRole)) {
                    is CreateUserResult.Success ->
                        call.respond(HttpStatusCode.Created, UserResponse(result.id, result.login, result.role))
                    is CreateUserResult.Error ->
                        call.respond(HttpStatusCode.fromValue(result.status), ErrorResponse(result.message))
                }
            }
        }
    }
}
