package org.eyetracker.test.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.eyetracker.record.dao.RecordDao.RecordItemStatsData
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.record.service.computeRoiMetrics
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestPassTokenDao
import org.eyetracker.test.dao.TestWithImages
import org.eyetracker.test.dto.MoveImageResponse
import org.eyetracker.test.dto.RoiStatEntry
import org.eyetracker.test.dto.RoiStatsResponse
import org.eyetracker.test.dto.TestImageInfo
import org.eyetracker.test.dto.TestImageResponse
import org.eyetracker.test.dto.TestListResponse
import org.eyetracker.test.dto.TestPassTokenResponse
import org.eyetracker.test.dto.TestResponse
import org.eyetracker.test.dto.UpdateRoisResponse
import java.io.File
import java.io.InputStream

private val lenientJson = Json { ignoreUnknownKeys = true }

sealed class TestResult {
    data class Success(val response: TestResponse) : TestResult()
    data class Error(val message: String, val status: Int) : TestResult()
}

sealed class ImageResult {
    data class Success(val response: TestImageResponse) : ImageResult()
    data class Error(val message: String, val status: Int) : ImageResult()
}

sealed class RoiStatsResult {
    data class Success(val response: RoiStatsResponse) : RoiStatsResult()
    data class Error(val message: String, val status: Int) : RoiStatsResult()
}

class TestService(
    private val testDao: TestDao,
    private val recordDao: RecordDao,
    private val testPassTokenDao: TestPassTokenDao,
    private val uploadDir: String,
) {
    fun create(
        name: String,
        coverStream: InputStream,
        coverExtension: String,
        userId: Int,
    ): TestResult {
        if (name.isBlank()) {
            return TestResult.Error("Name is required", 400)
        }

        val coverFilename = "cover.$coverExtension"
        val testWithImages = testDao.create(name, coverFilename, userId)
        val testId = testWithImages.test.id.value

        val testDir = File(uploadDir, "tests/$testId")
        try {
            testDir.mkdirs()
            coverStream.use { input ->
                File(testDir, coverFilename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            testDir.deleteRecursively()
            testDao.deleteById(testId)
            return TestResult.Error("Failed to save files: ${e.message}", 500)
        }

        return TestResult.Success(toResponse(testWithImages))
    }

    fun updateName(testId: Int, name: String): TestResult {
        if (name.isBlank()) return TestResult.Error("Name is required", 400)
        val updated = testDao.updateName(testId, name)
        if (!updated) return TestResult.Error("Test not found", 404)
        return TestResult.Success(toResponse(testDao.findById(testId)!!))
    }

    fun updateCover(testId: Int, coverStream: InputStream, coverExtension: String): TestResult {
        val testWithImages = testDao.findById(testId)
            ?: return TestResult.Error("Test not found", 404)

        val coverFilename = "cover.$coverExtension"
        val testDir = File(uploadDir, "tests/$testId")
        try {
            testDir.mkdirs()
            coverStream.use { input ->
                File(testDir, coverFilename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            return TestResult.Error("Failed to save cover: ${e.message}", 500)
        }

        testDao.updateCover(testId, coverFilename)
        return TestResult.Success(toResponse(testDao.findById(testId)!!))
    }

    fun addImage(testId: Int, imageStream: InputStream, imageExtension: String): ImageResult {
        val testWithImages = testDao.findById(testId)
            ?: return ImageResult.Error("Test not found", 404)

        val sortOrder = testWithImages.imageFilenames.size
        val filename = "${sortOrder.toString().padStart(3, '0')}.$imageExtension"

        val image = testDao.addImage(testId, filename)
            ?: return ImageResult.Error("Test not found", 404)

        val testDir = File(uploadDir, "tests/$testId")
        try {
            testDir.mkdirs()
            imageStream.use { input ->
                File(testDir, filename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            return ImageResult.Error("Failed to save image: ${e.message}", 500)
        }

        return ImageResult.Success(
            TestImageResponse(
                imageId = image.id.value,
                imageUrl = "/tests/$testId/images/$sortOrder",
                sortOrder = sortOrder,
            )
        )
    }

    fun reorderImage(imageId: Int, newPosition: Int): MoveImageResponse? {
        val finalPosition = testDao.reorderImage(imageId, newPosition) ?: return null
        return MoveImageResponse(imageId = imageId, sortOrder = finalPosition)
    }

    fun deleteImage(imageId: Int): String? {
        val data = testDao.deleteImage(imageId) ?: return "Image not found"
        val file = File(uploadDir, "tests/${data.testId}/${data.filename}")
        file.delete()
        return null
    }

    fun update(
        testId: Int,
        name: String,
        coverStream: InputStream,
        coverExtension: String,
        imageStreams: List<Pair<InputStream, String>>,
    ): TestResult {
        if (name.isBlank()) {
            return TestResult.Error("Name is required", 400)
        }

        testDao.findById(testId)
            ?: return TestResult.Error("Test not found", 404)

        val coverFilename = "cover.$coverExtension"
        val imageFilenames = imageStreams.mapIndexed { index, (_, ext) ->
            "${index.toString().padStart(3, '0')}.$ext"
        }

        val testDir = File(uploadDir, "tests/$testId")
        val tempDir = File(uploadDir, "tests/${testId}_tmp")

        try {
            tempDir.mkdirs()
            coverStream.use { input ->
                File(tempDir, coverFilename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            imageStreams.forEachIndexed { index, (stream, _) ->
                val filename = imageFilenames[index]
                stream.use { input ->
                    File(tempDir, filename).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return TestResult.Error("Failed to save files: ${e.message}", 500)
        }

        val updated = testDao.update(testId, name, coverFilename, imageFilenames)
            ?: run {
                tempDir.deleteRecursively()
                return TestResult.Error("Test not found", 404)
            }

        testDir.deleteRecursively()
        tempDir.renameTo(testDir)

        return TestResult.Success(toResponse(updated))
    }

    fun getAll(): TestListResponse {
        val tests = testDao.findAll()
        return TestListResponse(tests.map { toResponse(it) })
    }

    fun getById(testId: Int): TestResult {
        val testWithImages = testDao.findById(testId)
            ?: return TestResult.Error("Test not found", 404)
        return TestResult.Success(toResponse(testWithImages))
    }

    fun delete(testId: Int): TestResult {
        val testWithImages = testDao.findById(testId)
            ?: return TestResult.Error("Test not found", 404)

        val testDir = File(uploadDir, "tests/${testWithImages.test.id.value}")
        testDao.deleteById(testId)
        testDir.deleteRecursively()

        return TestResult.Success(toResponse(testWithImages))
    }

    fun updateImageRois(imageId: Int, rois: List<JsonObject>): UpdateRoisResponse? {
        val roiJson = Json.encodeToString(rois)
        val updated = testDao.updateImageRoi(imageId, roiJson)
        if (!updated) return null
        return UpdateRoisResponse(imageId, rois)
    }

    fun getRoiStats(testId: Int, requestingUserId: Int): RoiStatsResult {
        val testWithImages = testDao.findById(testId)
            ?: return RoiStatsResult.Error("Test not found", 404)
        if (testWithImages.test.userId != requestingUserId)
            return RoiStatsResult.Error("Access denied", 403)

        val imageRois = testDao.findImageRoisByTestId(testId)

        // Parse ROI definitions: name -> (color, firstFixationRequired)
        data class RoiDef(val color: String, val firstFixationRequired: Boolean)
        val roiDefs = linkedMapOf<String, RoiDef>()
        for ((_, roiJson) in imageRois) {
            roiJson ?: continue
            runCatching {
                Json.parseToJsonElement(roiJson).jsonArray.forEach { el ->
                    val obj = el.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                    roiDefs.putIfAbsent(
                        name,
                        RoiDef(
                            color = obj["color"]?.jsonPrimitive?.content ?: "",
                            firstFixationRequired = obj["first_fixation"]?.jsonPrimitive?.booleanOrNull ?: false,
                        ),
                    )
                }
            }
        }

        val items = recordDao.findItemsWithRecordForTest(testId)

        // Per record: which ROI names were hit
        val hitsByRecord = mutableMapOf<Int, MutableSet<String>>()
        val userByRecord = mutableMapOf<Int, String>()
        for (item in items) {
            userByRecord[item.recordId] = item.userLogin
            val metrics = runCatching {
                lenientJson.decodeFromString<RecordItemMetrics>(item.metricsJson)
            }.getOrDefault(RecordItemMetrics())
            for (roiMetric in metrics.roiMetrics) {
                val name = roiMetric["name"]?.jsonPrimitive?.content ?: continue
                val hit = roiMetric["hit"]?.jsonPrimitive?.booleanOrNull ?: false
                if (hit) hitsByRecord.getOrPut(item.recordId) { mutableSetOf() }.add(name)
            }
        }

        val totalRecords = userByRecord.size
        val uniqueUsers = userByRecord.values.distinct().size

        val rois = roiDefs.map { (name, def) ->
            RoiStatEntry(
                name = name,
                color = def.color,
                hits = hitsByRecord.values.count { name in it },
                total = totalRecords,
                firstFixationRequired = def.firstFixationRequired,
            )
        }

        return RoiStatsResult.Success(RoiStatsResponse(rois = rois, totalRecords = totalRecords, uniqueUsers = uniqueUsers))
    }

    fun syncRoiMetrics(testId: Int): TestResult {
        testDao.findById(testId)
            ?: return TestResult.Error("Test not found", 404)

        val imageRois = testDao.findImageRoisByTestId(testId)
        val items = recordDao.findItemsForTest(testId)

        for (item in items) {
            val roiJson = imageRois[item.imageId]
            val metrics = try {
                lenientJson.decodeFromString<RecordItemMetrics>(item.metricsJson)
            } catch (_: Exception) {
                RecordItemMetrics()
            }
            val newRoiMetrics = computeRoiMetrics(roiJson, metrics.fixations)
            val updated = metrics.copy(roiMetrics = newRoiMetrics)
            recordDao.updateItemMetrics(item.itemId, Json.encodeToString(updated))
        }

        // Return a dummy success; the controller will respond 204
        return TestResult.Success(
            TestResponse(testId, "", "", "", emptyList()),
        )
    }

    fun getCoverFile(testId: Int): File? {
        val testWithImages = testDao.findById(testId) ?: return null
        val file = File(uploadDir, "tests/$testId/${testWithImages.test.coverFilename}")
        return if (file.exists()) file else null
    }

    fun getImageFile(testId: Int, imageIndex: Int): File? {
        val testWithImages = testDao.findById(testId) ?: return null
        if (imageIndex < 0 || imageIndex >= testWithImages.imageFilenames.size) return null
        val filename = testWithImages.imageFilenames[imageIndex]
        val file = File(uploadDir, "tests/$testId/$filename")
        return if (file.exists()) file else null
    }

    fun getOrCreateToken(testId: Int): TestPassTokenResponse? {
        testDao.findById(testId) ?: return null
        val existing = testPassTokenDao.findByTestId(testId)
        if (existing != null) return TestPassTokenResponse(code = existing.code, testId = testId)
        var code: String
        do {
            code = (10_000_000..99_999_999).random().toString()
        } while (testPassTokenDao.findByCode(code) != null)
        testPassTokenDao.create(testId, code)
        return TestPassTokenResponse(code = code, testId = testId)
    }

    fun getTestByToken(code: String): TestResult {
        val token = testPassTokenDao.findByCode(code)
            ?: return TestResult.Error("Token not found", 404)
        val testWithImages = testDao.findById(token.testId)
            ?: return TestResult.Error("Test not found", 404)
        return TestResult.Success(toResponse(testWithImages))
    }

    private fun toResponse(testWithImages: TestWithImages): TestResponse {
        val testId = testWithImages.test.id.value
        val images = testWithImages.imageIds.indices.map { i ->
            TestImageInfo(
                id = testWithImages.imageIds[i],
                url = "/tests/$testId/images/$i",
                sortOrder = testWithImages.sortOrders.getOrElse(i) { i },
                rois = testWithImages.rois.getOrNull(i)?.let { roiStr ->
                    runCatching { Json.parseToJsonElement(roiStr).jsonArray.map { el -> el.jsonObject } }.getOrNull()
                } ?: emptyList(),
            )
        }
        return TestResponse(
            id = testId,
            name = testWithImages.test.name,
            coverUrl = "/tests/$testId/cover",
            createdAt = testWithImages.test.createdAt.toString(),
            images = images,
        )
    }
}
