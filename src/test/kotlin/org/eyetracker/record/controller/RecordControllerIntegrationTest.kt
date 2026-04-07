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
        assertEquals(TestFixtures.ADMIN_LOGIN, body["userLogin"]!!.jsonPrimitive.content)
        assertEquals(300000, body["durationMs"]!!.jsonPrimitive.long)
        assertEquals(2, body["items"]!!.jsonArray.size)
    }

    @Test
    fun `create record without auth returns 401`() = testApp { client ->
        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            setBody("""{"testId":1,"startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create record with empty items returns 400`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, _) = testData

        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":$testId,"startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[]}""")
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
            setBody("""{"testId":$testId,"startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":-1,"items":[{"imageId":${imageIds[0]},"metrics":{"placeholderMetric":1.0}}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create record with invalid testId returns 404`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":99999,"startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[{"imageId":1,"metrics":{"placeholderMetric":1.0}}]}""")
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
            setBody("""{"testId":$testId,"startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[{"imageId":99999,"metrics":{"placeholderMetric":1.0}}]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ===== GET /records =====

    @Test
    fun `list records returns paginated result`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordViaApi(client, token, testId, imageIds)
        createRecordViaApi(client, token, testId, imageIds)
        createRecordViaApi(client, token, testId, imageIds)

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

        createRecordViaApi(client, token, testId, imageIds)

        val response = client.get("/records?userLogin=${TestFixtures.ADMIN_LOGIN}") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["items"]!!.jsonArray.size)
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records filtered by userLogin returns empty for non-matching`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordViaApi(client, token, testId, imageIds)

        val response = client.get("/records?userLogin=nonexistent@test.com") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["items"]!!.jsonArray.size)
        assertEquals(0, body["total"]!!.jsonPrimitive.int)
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

    // ===== roi filter =====

    private val roiHitMetrics = """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],
        |"roiMetrics":[{"name":"hasGaze","hit":true,"color":"#00dc64","firstFixationRequired":false}]}"""
        .trimMargin().replace("\n", "")

    private val roiMissMetrics = """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],
        |"roiMetrics":[{"name":"hasGaze","hit":false,"color":"#00dc64","firstFixationRequired":false}]}"""
        .trimMargin().replace("\n", "")

    private val emptyMetrics =
        """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],"roiMetrics":[]}"""

    private suspend fun createRecordForImage(
        client: io.ktor.client.HttpClient,
        token: String,
        testId: Int,
        imageId: Int,
        metricsJson: String = emptyMetrics,
    ): io.ktor.client.statement.HttpResponse {
        return client.post("/records") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"testId":$testId,"startedAt":"2025-01-01T10:00:00Z","finishedAt":"2025-01-01T10:05:00Z","durationMs":300000,"items":[{"imageId":$imageId,"metrics":$metricsJson}]}""")
        }
    }

    @Test
    fun `list records filtered by roi true returns only matching records`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0], roiHitMetrics)  // roiMetrics hasGaze=true → matches
        createRecordForImage(client, token, testId, imageIds[1], emptyMetrics)   // no roiMetrics → no match

        val response = client.get("/records?roi.hasGaze=true") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records filtered by roi false returns only matching records`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0], roiMissMetrics) // roiMetrics hasGaze=false → matches
        createRecordForImage(client, token, testId, imageIds[1], roiHitMetrics)  // roiMetrics hasGaze=true  → no match

        val response = client.get("/records?roi.hasGaze=false") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records without roi filter returns all`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0], roiHitMetrics)
        createRecordForImage(client, token, testId, imageIds[1], emptyMetrics)

        val response = client.get("/records") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(2, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `suggest users filtered by roi returns matching logins`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0], roiHitMetrics)  // matches
        createRecordForImage(client, token, testId, imageIds[1], emptyMetrics)   // no match

        val response = client.get("/records/users/suggest?roi.hasGaze=true") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
        assertEquals(TestFixtures.ADMIN_LOGIN, body["items"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `suggest users without roi filter returns all`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0])
        createRecordForImage(client, token, testId, imageIds[1])

        val response = client.get("/records/users/suggest") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["total"]!!.jsonPrimitive.int)  // same user, deduplicated
    }

    // ===== userLoginContains filter =====

    @Test
    fun `list records filtered by userLoginContains returns substring matches`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0])

        val partial = TestFixtures.ADMIN_LOGIN.substring(0, 4)
        val response = client.get("/records?userLoginContains=$partial") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["total"]!!.jsonPrimitive.int)
    }

    @Test
    fun `list records filtered by userLoginContains no match returns empty`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        createRecordForImage(client, token, testId, imageIds[0])

        val response = client.get("/records?userLoginContains=zzznomatch") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["total"]!!.jsonPrimitive.int)
    }

    // ===== POST /tests/{id}/sync-roi =====

    @Test
    fun `sync roi metrics computes roi metrics from fixations and roi polygons`() = testApp { client ->
        val (token, testData) = setupTestWithImages(client)
        val (testId, imageIds) = testData

        // Set a ROI polygon covering the center of the image (normalised coords)
        val roiJson = """{"name":"center","color":"#00dc64","first_fixation":false,"points":[{"x":0.25,"y":0.25},{"x":0.75,"y":0.25},{"x":0.75,"y":0.75},{"x":0.25,"y":0.75}]}"""
        client.patch("/tests/images/${imageIds[0]}/roi") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"roi":"${roiJson.replace("\"", "\\\"")}"}""")
        }

        // Create a record whose fixation falls inside the polygon
        val metricsWithFixation = """{"gazeGroups":[],"fixations":[{"center":{"x":0.5,"y":0.5},"is_first":true}],"firstFixationTimeMs":100,"saccades":[],"roiMetrics":[]}"""
        val createResp = createRecordForImage(client, token, testId, imageIds[0], metricsWithFixation)
        val recordId = Json.parseToJsonElement(createResp.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int

        // Call sync
        val syncResp = client.post("/tests/$testId/sync-roi") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NoContent, syncResp.status)

        // Load the record and verify roiMetrics were computed
        val detail = client.get("/records/$recordId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        val detailBody = Json.parseToJsonElement(detail.bodyAsText()).jsonObject
        val item = detailBody["items"]!!.jsonArray[0].jsonObject
        val roiMetrics = item["metrics"]!!.jsonObject["roiMetrics"]!!.jsonArray
        assertEquals(1, roiMetrics.size)
        val entry = roiMetrics[0].jsonObject
        assertEquals("center", entry["name"]!!.jsonPrimitive.content)
        assertEquals(true, entry["hit"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `sync roi metrics returns 404 for unknown test`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.post("/tests/99999/sync-roi") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `sync roi metrics returns 403 for non-admin`() = testApp { client ->
        val (_, testData) = setupTestWithImages(client)
        val (testId, _) = testData
        val userToken = registerUser(client)
        val response = client.post("/tests/$testId/sync-roi") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(userToken))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
