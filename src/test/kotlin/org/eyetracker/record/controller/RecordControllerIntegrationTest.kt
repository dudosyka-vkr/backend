package org.eyetracker.record.controller

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.eyetracker.base.IntegrationTestBase
import org.eyetracker.base.TestFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordControllerIntegrationTest : IntegrationTestBase() {

    private suspend fun setupTestWithImages(
        client: io.ktor.client.HttpClient,
    ): Pair<String, Pair<Int, List<Int>>> {
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "RecordTest")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int
        val imageIds = getImageIdsFromDb(testId)
        return token to (testId to imageIds)
    }

    // ===== POST /records =====

    @Test
    fun `create record returns 201`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        val response = createRecordViaApi(client, token, testId, imageIds)
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["id"]!!.jsonPrimitive.int > 0)
        assertEquals(testId, body["testId"]!!.jsonPrimitive.int)
        assertEquals("recorder@test.com", body["userLogin"]!!.jsonPrimitive.content)
        assertEquals(300000, body["durationMs"]!!.jsonPrimitive.long)
        assertEquals(2, body["items"]!!.jsonArray.size)
    }

    @Test
    fun `create record without auth returns 401`() = testApp { client ->
        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            setBody("""{"testId":1,"userLogin":"u","startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create record with blank userLogin returns 400`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        val response = createRecordViaApi(client, token, testId, imageIds, userLogin = "   ")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create record with empty items returns 400`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, _) = testData

        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":$testId,"userLogin":"u@test.com","startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create record with negative duration returns 400`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":$testId,"userLogin":"u@test.com","startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":-1,"items":[{"imageId":${imageIds[0]},"metrics":{"placeholderMetric":1.0}}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create record with invalid testId returns 404`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":99999,"userLogin":"u@test.com","startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[{"imageId":1,"metrics":{"placeholderMetric":1.0}}]}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `create record with invalid imageId returns 400`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, _) = testData

        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":$testId,"userLogin":"u@test.com","startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[{"imageId":99999,"metrics":{"placeholderMetric":1.0}}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ===== GET /records =====

    @Test
    fun `list records returns paginated result`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordViaApi(client, token, testId, imageIds, userLogin = "a@test.com")
        createRecordViaApi(client, token, testId, imageIds, userLogin = "b@test.com")
        createRecordViaApi(client, token, testId, imageIds, userLogin = "c@test.com")

        val response = client.get("/records?page=1&pageSize=2") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(2, body["items"]!!.jsonArray.size)
        assertEquals(3, body["total"]!!.jsonPrimitive.int)
        assertEquals(1, body["page"]!!.jsonPrimitive.int)
        assertEquals(2, body["pageSize"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records filtered by userLogin`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordViaApi(client, token, testId, imageIds, userLogin = "alice@test.com")
        createRecordViaApi(client, token, testId, imageIds, userLogin = "bob@test.com")

        val response = client.get("/records?userLogin=alice@test.com") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["items"]!!.jsonArray.size)
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records filtered by time range`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordViaApi(client, token, testId, imageIds)

        val response = client.get("/records?from=2025-01-01T09:00:00Z&to=2025-01-01T11:00:00Z") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records returns empty when none exist`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/records") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["items"]!!.jsonArray.size)
        assertEquals(0, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records without auth returns 401`() = testApp { client ->
        val response = client.get("/records")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== GET /records/{id} =====

    @Test
    fun `get record by id returns detail with items`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        val createResponse = createRecordViaApi(client, token, testId, imageIds)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val recordId = created["id"]!!.jsonPrimitive.int

        val response = client.get("/records/$recordId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(recordId, body["id"]!!.jsonPrimitive.int)
        assertEquals(2, body["items"]!!.jsonArray.size)
        val firstItem = body["items"]!!.jsonArray[0].jsonObject
        assertTrue(firstItem.containsKey("imageId"))
        assertTrue(firstItem.containsKey("metrics"))
    }

    @Test
    fun `get record not found returns 404`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/records/99999") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get record invalid id returns 400`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/records/abc") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get record without auth returns 401`() = testApp { client ->
        val response = client.get("/records/1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
