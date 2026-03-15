package org.eyetracker.auth.controller

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eyetracker.base.IntegrationTestBase
import org.eyetracker.base.TestFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthControllerIntegrationTest : IntegrationTestBase() {

    // ===== POST /auth/register =====

    @Test
    fun `register success returns 201 with token`() = testApp { client ->
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"new@test.com","password":"Pass123!"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["token"])
    }

    @Test
    fun `register duplicate returns 409`() = testApp { client ->
        registerUser(client, "dup@test.com")
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"dup@test.com","password":"Pass123!"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("User already exists", body["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `registered token is usable for authenticated endpoints`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ===== POST /auth/login =====

    @Test
    fun `login success returns 200 with token`() = testApp { client ->
        registerUser(client, "user@test.com", "Pass123!")
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"user@test.com","password":"Pass123!"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["token"])
    }

    @Test
    fun `login with wrong password returns 401`() = testApp { client ->
        registerUser(client, "user@test.com", "Pass123!")
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"user@test.com","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login with nonexistent user returns 401`() = testApp { client ->
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"nobody@test.com","password":"pass"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== POST /auth/users =====

    @Test
    fun `SUPER_ADMIN creates ADMIN returns 201`() = testApp { client ->
        val saToken = getSuperAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(saToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"newadmin@test.com","password":"Pass123!","role":"ADMIN"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("ADMIN", body["role"]?.jsonPrimitive?.content)
        assertEquals("newadmin@test.com", body["login"]?.jsonPrimitive?.content)
    }

    @Test
    fun `SUPER_ADMIN creates USER returns 201`() = testApp { client ->
        val saToken = getSuperAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(saToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"newuser@test.com","password":"Pass123!","role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("USER", body["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ADMIN creates USER returns 201`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(adminToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"newuser@test.com","password":"Pass123!","role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `ADMIN cannot create ADMIN returns 403`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(adminToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"ADMIN"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `ADMIN cannot create SUPER_ADMIN returns 403`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(adminToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"SUPER_ADMIN"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `SUPER_ADMIN cannot create SUPER_ADMIN returns 403`() = testApp { client ->
        val saToken = getSuperAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(saToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"SUPER_ADMIN"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `USER cannot create users returns 403`() = testApp { client ->
        val userToken = registerUser(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(userToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `create user without auth returns 401`() = testApp { client ->
        val response = client.post("/auth/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create user with invalid token returns 401`() = testApp { client ->
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create duplicate user returns 409`() = testApp { client ->
        val adminToken = getAdminToken(client)
        // Create first user
        client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(adminToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"existing@test.com","password":"Pass123!","role":"USER"}""")
        }
        // Try creating same user again
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(adminToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"existing@test.com","password":"Pass123!","role":"USER"}""")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `create user with invalid role returns 400`() = testApp { client ->
        val saToken = getSuperAdminToken(client)
        val response = client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(saToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"x@test.com","password":"Pass123!","role":"MODERATOR"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `created user can login`() = testApp { client ->
        val saToken = getSuperAdminToken(client)
        client.post("/auth/users") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(saToken))
            contentType(ContentType.Application.Json)
            setBody("""{"login":"loginable@test.com","password":"Pass123!","role":"USER"}""")
        }
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"loginable@test.com","password":"Pass123!"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val body = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        assertNotNull(body["token"])
    }
}
