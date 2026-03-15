package org.eyetracker.base

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class DatabaseTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("eyetracker_test")
            withUsername("test")
            withPassword("test")
        }
    }

    @BeforeEach
    fun setupDatabase() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .cleanDisabled(false)
            .load()
            .also { it.clean(); it.migrate() }

        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )
    }

    @AfterEach
    fun cleanupDatabase() {
        transaction {
            exec("TRUNCATE test_images, tests, users RESTART IDENTITY CASCADE")
        }
    }
}
