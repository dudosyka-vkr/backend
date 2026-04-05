package org.eyetracker.base

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

private const val JDBC_URL = "jdbc:postgresql://localhost:5432/eyetracker_test"
private const val DB_USER = "postgres"
private const val DB_PASSWORD = "my-secret-pw"

abstract class DatabaseTestBase {

    @BeforeEach
    fun setupDatabase() {
        Flyway.configure()
            .dataSource(JDBC_URL, DB_USER, DB_PASSWORD)
            .cleanDisabled(false)
            .load()
            .also { it.clean(); it.migrate() }

        Database.connect(
            url = JDBC_URL,
            driver = "org.postgresql.Driver",
            user = DB_USER,
            password = DB_PASSWORD,
        )
    }

    @AfterEach
    fun cleanupDatabase() {
        transaction {
            exec("TRUNCATE record_items, records, test_images, tests, users RESTART IDENTITY CASCADE")
        }
    }
}
