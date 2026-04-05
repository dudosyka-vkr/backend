package org.eyetracker.test.controller

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.eyetracker.base.IntegrationTestBase
import org.eyetracker.base.TestFixtures
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestControllerIntegrationTest : IntegrationTestBase() {

    // ===== POST /tests =====

    @Test
    fun `admin creates test returns 201`() = testApp { client ->
        val token = getAdminToken(client)
        val response = createTestViaApi(client, token, "My Test")
        assertEquals(HttpStatusCode.Created, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["id"]!!.jsonPrimitive.int > 0)
        assertEquals("My Test", body["name"]!!.jsonPrimitive.content)
        assertTrue(body["coverUrl"]!!.jsonPrimitive.content.contains("/cover"))
        assertEquals(2, body["imageUrls"]!!.jsonArray.size)
    }

    @Test
    fun `super admin creates test returns 201`() = testApp { client ->
        val token = getSuperAdminToken(client)
        val response = createTestViaApi(client, token, "SA Test")
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `user cannot create test returns 403`() = testApp { client ->
        val token = registerUser(client)
        val response = createTestViaApi(client, token)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `create test without auth returns 401`() = testApp { client ->
        val response = createTestViaApi(client, "invalid-token")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create test without name returns 400`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.submitFormWithBinaryData(
            url = "/tests",
            formData = formData {
                append("cover", TestFixtures.sampleCoverBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"cover.png\"")
                })
                append("images", TestFixtures.sampleImageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create test without cover returns 400`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.submitFormWithBinaryData(
            url = "/tests",
            formData = formData {
                append("name", "No Cover")
                append("images", TestFixtures.sampleImageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create test without images returns 400`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.submitFormWithBinaryData(
            url = "/tests",
            formData = formData {
                append("name", "No Images")
                append("cover", TestFixtures.sampleCoverBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"cover.png\"")
                })
            }
        ) {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ===== PUT /tests/{id} =====

    @Test
    fun `admin updates test returns 200`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "Original")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = updateTestViaApi(client, token, testId, "Updated")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Updated", body["name"]!!.jsonPrimitive.content)
        assertEquals(1, body["imageUrls"]!!.jsonArray.size)
    }

    @Test
    fun `super admin updates test returns 200`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val createResponse = createTestViaApi(client, adminToken, "Original")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val saToken = getSuperAdminToken(client)
        val response = updateTestViaApi(client, saToken, testId, "SA Updated")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `user cannot update test returns 403`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val createResponse = createTestViaApi(client, adminToken, "Original")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val userToken = registerUser(client)
        val response = updateTestViaApi(client, userToken, testId, "Nope")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `update test without auth returns 401`() = testApp { client ->
        val response = updateTestViaApi(client, "invalid-token", 1, "Nope")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `update nonexistent test returns 404`() = testApp { client ->
        val token = getAdminToken(client)
        val response = updateTestViaApi(client, token, 99999, "Nope")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `update test without name returns 400`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "Original")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.submitFormWithBinaryData(
            url = "/tests/$testId",
            formData = formData {
                append("cover", TestFixtures.sampleCoverBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"cover.png\"")
                })
                append("images", TestFixtures.sampleImageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"image.jpg\"")
                })
            }
        ) {
            method = HttpMethod.Put
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `update test replaces files on disk`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "Original")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        // Verify original has 2 images
        assertEquals(2, created["imageUrls"]!!.jsonArray.size)

        // Update with 1 image
        val updateResponse = updateTestViaApi(client, token, testId, "Updated")
        assertEquals(HttpStatusCode.OK, updateResponse.status)

        val updated = Json.parseToJsonElement(updateResponse.bodyAsText()).jsonObject
        assertEquals(1, updated["imageUrls"]!!.jsonArray.size)

        // Verify old files are gone and new ones exist
        val coverResponse = client.get("/tests/$testId/cover") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, coverResponse.status)

        val img0Response = client.get("/tests/$testId/images/0") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, img0Response.status)

        // Old image index 1 should be gone
        val img1Response = client.get("/tests/$testId/images/1") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, img1Response.status)
    }

    // ===== GET /tests =====

    @Test
    fun `list tests returns all tests`() = testApp { client ->
        val token = getAdminToken(client)
        createTestViaApi(client, token, "Test 1")
        createTestViaApi(client, token, "Test 2")

        val response = client.get("/tests") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(2, body["tests"]!!.jsonArray.size)
    }

    @Test
    fun `list tests returns empty when none exist`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(0, body["tests"]!!.jsonArray.size)
    }

    @Test
    fun `list tests without auth returns 401`() = testApp { client ->
        val response = client.get("/tests")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== GET /tests/{id} =====

    @Test
    fun `get test by id returns test`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "Single Test")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.get("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Single Test", body["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get test not found returns 404`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests/99999") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get test invalid id returns 400`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests/abc") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get test without auth returns 401`() = testApp { client ->
        val response = client.get("/tests/1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== DELETE /tests/{id} =====

    @Test
    fun `admin deletes test returns 204`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "ToDelete")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.delete("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        // Verify it's gone
        val getResponse = client.get("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `super admin deletes test returns 204`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val createResponse = createTestViaApi(client, adminToken, "SADelete")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val saToken = getSuperAdminToken(client)
        val response = client.delete("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(saToken))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `user cannot delete test returns 403`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val createResponse = createTestViaApi(client, adminToken, "NoDelete")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val userToken = registerUser(client)
        val response = client.delete("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(userToken))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `delete nonexistent test returns 404`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.delete("/tests/99999") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete without auth returns 401`() = testApp { client ->
        val response = client.delete("/tests/1")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== GET /tests/{id}/cover =====

    @Test
    fun `get cover returns file`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.get("/tests/$testId/cover") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.readRawBytes().isNotEmpty())
    }

    @Test
    fun `get cover of nonexistent test returns 404`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests/99999/cover") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get cover without auth returns 401`() = testApp { client ->
        val response = client.get("/tests/1/cover")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== GET /tests/{id}/images/{index} =====

    @Test
    fun `get image at index 0 returns file`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.get("/tests/$testId/images/0") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.readRawBytes().isNotEmpty())
    }

    @Test
    fun `get image at index 1 returns file`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.get("/tests/$testId/images/1") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.readRawBytes().isNotEmpty())
    }

    @Test
    fun `get image out of bounds returns 404`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        val response = client.get("/tests/$testId/images/99") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get image of nonexistent test returns 404`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests/99999/images/0") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get image with invalid index returns 400`() = testApp { client ->
        val token = registerUser(client)
        val response = client.get("/tests/1/images/abc") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get image without auth returns 401`() = testApp { client ->
        val response = client.get("/tests/1/images/0")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ===== PATCH /tests/images/{id}/roi =====

    @Test
    fun `admin updates roi returns 200 with saved value`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "ROI Test")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int
        val imageId = created["imageIds"]!!.jsonArray[0].jsonPrimitive.int

        val response = client.patch("/tests/images/$imageId/roi") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"roi":"{\"x\":10,\"y\":20,\"w\":100,\"h\":50}"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(imageId, body["imageId"]!!.jsonPrimitive.int)
        assertEquals("""{"x":10,"y":20,"w":100,"h":50}""", body["roi"]!!.jsonPrimitive.content)
    }

    @Test
    fun `roi is returned in test response after update`() = testApp { client ->
        val token = getAdminToken(client)
        val createResponse = createTestViaApi(client, token, "ROI Test")
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int
        val imageId = created["imageIds"]!!.jsonArray[0].jsonPrimitive.int

        // Initially all areas are null
        val areas = created["rois"]!!.jsonArray
        assertTrue(areas.all { it is JsonNull })

        // Set roi for image 0
        client.patch("/tests/images/$imageId/roi") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"roi":"my-roi"}""")
        }

        // Fetch test and verify area is returned
        val getResponse = client.get("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        val body = Json.parseToJsonElement(getResponse.bodyAsText()).jsonObject
        assertEquals("my-roi", body["rois"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `update roi for nonexistent image returns 404`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.patch("/tests/images/99999/roi") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"roi":"data"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `user cannot update roi returns 403`() = testApp { client ->
        val adminToken = getAdminToken(client)
        val createResponse = createTestViaApi(client, adminToken)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val imageId = created["imageIds"]!!.jsonArray[0].jsonPrimitive.int

        val userToken = registerUser(client)
        val response = client.patch("/tests/images/$imageId/roi") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(userToken))
            setBody("""{"roi":"data"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `update roi without auth returns 401`() = testApp { client ->
        val response = client.patch("/tests/images/1/roi") {
            contentType(ContentType.Application.Json)
            setBody("""{"roi":"data"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `update roi with invalid id returns 400`() = testApp { client ->
        val token = getAdminToken(client)
        val response = client.patch("/tests/images/abc/roi") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
            setBody("""{"roi":"data"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ===== Full Lifecycle Test =====

    @Test
    fun `full lifecycle - create, list, get, download, delete, verify gone`() = testApp { client ->
        val token = getAdminToken(client)

        // 1. Create test
        val createResponse = createTestViaApi(client, token, "Lifecycle Test")
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val testId = created["id"]!!.jsonPrimitive.int

        // 2. List - should contain the test
        val listResponse = client.get("/tests") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val tests = Json.parseToJsonElement(listResponse.bodyAsText()).jsonObject["tests"]!!.jsonArray
        assertEquals(1, tests.size)
        assertEquals("Lifecycle Test", tests[0].jsonObject["name"]!!.jsonPrimitive.content)

        // 3. Get by ID
        val getResponse = client.get("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        // 4. Download cover
        val coverResponse = client.get("/tests/$testId/cover") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, coverResponse.status)
        assertTrue(coverResponse.readRawBytes().isNotEmpty())

        // 5. Download images
        val img0Response = client.get("/tests/$testId/images/0") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, img0Response.status)

        val img1Response = client.get("/tests/$testId/images/1") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.OK, img1Response.status)

        // 6. Delete
        val deleteResponse = client.delete("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // 7. Verify gone
        val afterDelete = client.get("/tests/$testId") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        assertEquals(HttpStatusCode.NotFound, afterDelete.status)

        // 8. Verify list is empty
        val emptyList = client.get("/tests") {
            header(HttpHeaders.Authorization, TestFixtures.authHeader(token))
        }
        val emptyTests = Json.parseToJsonElement(emptyList.bodyAsText()).jsonObject["tests"]!!.jsonArray
        assertEquals(0, emptyTests.size)

        // 9. Verify files are deleted
        val testDir = File("build/test-uploads/tests/$testId")
        assertTrue(!testDir.exists())
    }
}
