package org.eyetracker.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.eyetracker.auth.controller.authRoutes
import org.eyetracker.auth.service.AuthService
import org.eyetracker.record.controller.recordRoutes
import org.eyetracker.record.service.RecordService
import org.eyetracker.test.controller.testRoutes
import org.eyetracker.test.service.TestService
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val authService by inject<AuthService>()
    val testService by inject<TestService>()
    val recordService by inject<RecordService>()

    routing {
        get("/") {
            call.respondText("Hello, Ktor!")
        }
        authRoutes(authService)
        testRoutes(testService)
        recordRoutes(recordService)
    }
}
