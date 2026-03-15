package org.eyetracker.plugins

import io.ktor.server.application.*
import org.eyetracker.auth.dao.UserDao
import org.eyetracker.auth.service.AuthService
import org.eyetracker.auth.service.JwtConfig
import org.koin.dsl.module

fun Application.buildAppModule() = module {
    single {
        JwtConfig(
            secret = this@buildAppModule.environment.config.property("jwt.secret").getString(),
            issuer = this@buildAppModule.environment.config.property("jwt.issuer").getString(),
            audience = this@buildAppModule.environment.config.property("jwt.audience").getString(),
            realm = this@buildAppModule.environment.config.property("jwt.realm").getString(),
        )
    }
    single { UserDao() }
    single { AuthService(get(), get()) }
}
