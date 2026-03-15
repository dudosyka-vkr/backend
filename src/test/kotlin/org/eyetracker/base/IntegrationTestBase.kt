package org.eyetracker.base

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.eyetracker.module
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mindrot.jbcrypt.BCrypt
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("eyetracker_integration")
            withUsername("test")
            withPassword("test")
        }
    }

    private val uploadDir = "build/test-uploads"

    @BeforeEach
    fun setupDb() {
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .cleanDisabled(false)
            .load()
            .also { it.clean(); it.migrate() }
    }

    @AfterEach
    fun cleanup() {
        // Clean DB
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )
        transaction {
            exec("TRUNCATE test_images, tests, users RESTART IDENTITY CASCADE")
        }
        // Clean upload files
        File(uploadDir).deleteRecursively()
    }

    fun testApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "ktor.deployment.port" to "0",
                "jwt.secret" to "test-secret-key-for-testing-only",
                "jwt.issuer" to "http://localhost/",
                "jwt.audience" to "http://localhost/",
                "jwt.realm" to "Test API",
                "storage.uploadDir" to uploadDir,
                "database.url" to postgres.jdbcUrl,
                "database.driver" to "org.postgresql.Driver",
                "database.user" to postgres.username,
                "database.password" to postgres.password,
            )
        }
        application {
            module()
        }
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
        block(client)
    }

    suspend fun registerUser(client: HttpClient, login: String = TestFixtures.VALID_LOGIN, password: String = TestFixtures.VALID_PASSWORD): String {
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("login" to login, "password" to password))
        }
        val body = Json.parseToJsonElement(response.bodyAsText())
        return body.jsonObject["token"]!!.jsonPrimitive.content
    }

    suspend fun loginUser(client: HttpClient, login: String, password: String): String {
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("login" to login, "password" to password))
        }
        val body = Json.parseToJsonElement(response.bodyAsText())
        return body.jsonObject["token"]!!.jsonPrimitive.content
    }

    fun insertUserDirectly(login: String, password: String, role: String) {
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )
        val hashed = BCrypt.hashpw(password, BCrypt.gensalt())
        transaction {
            exec("INSERT INTO users (email, password_hash, role, created_at) VALUES ('$login', '$hashed', '$role', NOW())")
        }
    }

    suspend fun getAdminToken(client: HttpClient): String {
        insertUserDirectly(TestFixtures.ADMIN_LOGIN, TestFixtures.VALID_PASSWORD, "ADMIN")
        return loginUser(client, TestFixtures.ADMIN_LOGIN, TestFixtures.VALID_PASSWORD)
    }

    suspend fun getSuperAdminToken(client: HttpClient): String {
        insertUserDirectly(TestFixtures.SUPER_ADMIN_LOGIN, TestFixtures.VALID_PASSWORD, "SUPER_ADMIN")
        return loginUser(client, TestFixtures.SUPER_ADMIN_LOGIN, TestFixtures.VALID_PASSWORD)
    }

    suspend fun updateTestViaApi(
        client: HttpClient,
        token: String,
        testId: Int,
        name: String = "Updated Test",
        coverBytes: ByteArray = TestFixtures.sampleCoverBytes,
        imageBytesList: List<ByteArray> = listOf(TestFixtures.sampleImageBytes),
    ): HttpResponse {
        return client.submitFormWithBinaryData(
            url = "/tests/$testId",
            formData = formData {
                append("name", name)
                append("cover", coverBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"cover.png\"")
                })
                imageBytesList.forEach { imageBytes ->
                    append("images", imageBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                    })
                }
            }
        ) {
            method = HttpMethod.Put
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
    }

    suspend fun createTestViaApi(
        client: HttpClient,
        token: String,
        name: String = "Test",
        coverBytes: ByteArray = TestFixtures.sampleCoverBytes,
        imageBytesList: List<ByteArray> = listOf(TestFixtures.sampleImageBytes, TestFixtures.sampleImageBytes),
    ): HttpResponse {
        return client.submitFormWithBinaryData(
            url = "/tests",
            formData = formData {
                append("name", name)
                append("cover", coverBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"cover.png\"")
                })
                imageBytesList.forEach { imageBytes ->
                    append("images", imageBytes, Headers.build {
                        append(HttpHeaders.ContentType, "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                    })
                }
            }
        ) {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
    }
}
