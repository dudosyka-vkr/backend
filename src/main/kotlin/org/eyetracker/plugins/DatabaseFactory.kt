package org.eyetracker.plugins

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabaseFactory() {
    val url = environment.config.property("database.url").getString()
    val driver = environment.config.property("database.driver").getString()
    val user = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    Flyway.configure()
        .dataSource(url, user, password)
        .load()
        .migrate()

    Database.connect(
        url = url,
        driver = driver,
        user = user,
        password = password,
    )
}
