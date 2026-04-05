package org.eyetracker.test.service

import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestWithImages
import org.eyetracker.test.dto.TestListResponse
import org.eyetracker.test.dto.TestResponse
import org.eyetracker.test.dto.UpdateFixationAreaResponse
import java.io.File
import java.io.InputStream

sealed class TestResult {
    data class Success(val response: TestResponse) : TestResult()
    data class Error(val message: String, val status: Int) : TestResult()
}

class TestService(
    private val testDao: TestDao,
    private val uploadDir: String,
) {
    fun create(
        name: String,
        coverStream: InputStream,
        coverExtension: String,
        imageStreams: List<Pair<InputStream, String>>,
        userId: Int,
    ): TestResult {
        if (name.isBlank()) {
            return TestResult.Error("Name is required", 400)
        }
        if (imageStreams.isEmpty()) {
            return TestResult.Error("At least one image is required", 400)
        }

        val coverFilename = "cover.$coverExtension"
        val imageFilenames = imageStreams.mapIndexed { index, (_, ext) ->
            "${index.toString().padStart(3, '0')}.$ext"
        }

        val testWithImages = testDao.create(name, coverFilename, imageFilenames, userId)
        val testId = testWithImages.test.id.value

        val testDir = File(uploadDir, "tests/$testId")
        try {
            testDir.mkdirs()
            coverStream.use { input ->
                File(testDir, coverFilename).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            imageStreams.forEachIndexed { index, (stream, _) ->
                val filename = imageFilenames[index]
                stream.use { input ->
                    File(testDir, filename).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            testDir.deleteRecursively()
            testDao.deleteById(testId)
            return TestResult.Error("Failed to save files: ${e.message}", 500)
        }

        return TestResult.Success(toResponse(testWithImages))
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
        if (imageStreams.isEmpty()) {
            return TestResult.Error("At least one image is required", 400)
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

    fun updateImageFixationArea(imageId: Int, fixationTrackingArea: String): UpdateFixationAreaResponse? {
        val updated = testDao.updateImageFixationArea(imageId, fixationTrackingArea)
        if (!updated) return null
        return UpdateFixationAreaResponse(imageId, fixationTrackingArea)
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

    private fun toResponse(testWithImages: TestWithImages): TestResponse {
        val testId = testWithImages.test.id.value
        return TestResponse(
            id = testId,
            name = testWithImages.test.name,
            coverUrl = "/tests/$testId/cover",
            imageUrls = testWithImages.imageFilenames.indices.map { "/tests/$testId/images/$it" },
            imageIds = testWithImages.imageIds,
            fixationTrackingAreas = testWithImages.fixationTrackingAreas,
            createdAt = testWithImages.test.createdAt.toString(),
        )
    }
}
