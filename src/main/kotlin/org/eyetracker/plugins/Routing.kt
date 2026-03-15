package org.eyetracker.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.auth.controller.authRoutes
import org.eyetracker.auth.service.AuthService
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val authService by inject<AuthService>()

    routing {
        get("/") {
            call.respondText("Hello, Ktor!")
        }
        authRoutes(authService)
    }
}
