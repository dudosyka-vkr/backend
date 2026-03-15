package org.eyetracker

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.eyetracker.plugins.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureSerialization()
    configureSecurity()
    configureDatabaseFactory()
    configureRouting()
}
