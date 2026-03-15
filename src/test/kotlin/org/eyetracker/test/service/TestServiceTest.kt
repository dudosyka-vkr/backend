package org.eyetracker.test.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestEntity
import org.eyetracker.test.dao.TestTable
import org.eyetracker.test.dao.TestWithImages
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestServiceTest {

    private lateinit var testDao: TestDao
    private lateinit var testService: TestService
    private val uploadDir = "build/test-service-uploads"

    @BeforeEach
    fun setup() {
        testDao = mockk()
        testService = TestService(testDao, uploadDir)
        File(uploadDir).mkdirs()
    }

    @AfterEach
    fun cleanup() {
        File(uploadDir).deleteRecursively()
    }

    private fun mockTestEntity(id: Int, name: String, coverFilename: String, userId: Int): TestEntity {
        val entity = mockk<TestEntity>()
        every { entity.id } returns EntityID(id, TestTable)
        every { entity.name } returns name
        every { entity.coverFilename } returns coverFilename
        every { entity.userId } returns userId
        every { entity.createdAt } returns Clock.System.now()
        return entity
    }

    private fun mockTestWithImages(id: Int, name: String, coverFilename: String, imageFilenames: List<String>, userId: Int = 1): TestWithImages {
        return TestWithImages(mockTestEntity(id, name, coverFilename, userId), imageFilenames)
    }

    private fun dummyStream(content: String = "data"): InputStream = ByteArrayInputStream(content.toByteArray())

    // ===== create() =====

    @Test
    fun `create success with cover and images`() {
        val twi = mockTestWithImages(1, "My Test", "cover.jpg", listOf("000.jpg", "001.jpg"))
        every { testDao.create(any(), any(), any(), any()) } returns twi

        val result = testService.create(
            "My Test", dummyStream(), "jpg",
            listOf(dummyStream() to "jpg", dummyStream() to "jpg"), 1
        )

        assertIs<TestResult.Success>(result)
        assertEquals(1, result.response.id)
        assertEquals("My Test", result.response.name)
        assertEquals("/tests/1/cover", result.response.coverUrl)
        assertEquals(2, result.response.imageUrls.size)
        assertTrue(File(uploadDir, "tests/1/cover.jpg").exists())
        assertTrue(File(uploadDir, "tests/1/000.jpg").exists())
        assertTrue(File(uploadDir, "tests/1/001.jpg").exists())
    }

    @Test
    fun `create fails with blank name`() {
        val result = testService.create("   ", dummyStream(), "jpg", listOf(dummyStream() to "jpg"), 1)
        assertIs<TestResult.Error>(result)
        assertEquals(400, result.status)
        assertEquals("Name is required", result.message)
        verify(exactly = 0) { testDao.create(any(), any(), any(), any()) }
    }

    @Test
    fun `create fails with empty image list`() {
        val result = testService.create("Valid", dummyStream(), "jpg", emptyList(), 1)
        assertIs<TestResult.Error>(result)
        assertEquals(400, result.status)
        assertEquals("At least one image is required", result.message)
        verify(exactly = 0) { testDao.create(any(), any(), any(), any()) }
    }

    @Test
    fun `create rolls back on cover IO failure`() {
        val twi = mockTestWithImages(2, "Fail", "cover.jpg", listOf("000.jpg"))
        every { testDao.create(any(), any(), any(), any()) } returns twi
        every { testDao.deleteById(2) } returns true

        val badStream = mockk<InputStream>()
        every { badStream.read(any<ByteArray>()) } throws IOException("disk full")
        every { badStream.read(any<ByteArray>(), any(), any()) } throws IOException("disk full")
        every { badStream.close() } returns Unit

        val result = testService.create("Fail", badStream, "jpg", listOf(dummyStream() to "jpg"), 1)
        assertIs<TestResult.Error>(result)
        assertEquals(500, result.status)
        verify { testDao.deleteById(2) }
    }

    @Test
    fun `create verifies image filename padding`() {
        val filenames = (0..10).map { "${it.toString().padStart(3, '0')}.jpg" }
        val twi = mockTestWithImages(3, "Padded", "cover.jpg", filenames)
        every { testDao.create(any(), any(), any(), any()) } returns twi

        val streams = (0..10).map { dummyStream() to "jpg" }
        val result = testService.create("Padded", dummyStream(), "jpg", streams, 1)

        assertIs<TestResult.Success>(result)
        assertEquals(11, result.response.imageUrls.size)
        assertTrue(File(uploadDir, "tests/3/010.jpg").exists())
    }

    @Test
    fun `create with different file extensions`() {
        val twi = mockTestWithImages(4, "ExtTest", "cover.png", listOf("000.webp", "001.webp"))
        every { testDao.create(any(), any(), any(), any()) } returns twi

        val result = testService.create(
            "ExtTest", dummyStream(), "png",
            listOf(dummyStream() to "webp", dummyStream() to "webp"), 1
        )

        assertIs<TestResult.Success>(result)
        assertTrue(File(uploadDir, "tests/4/cover.png").exists())
        assertTrue(File(uploadDir, "tests/4/000.webp").exists())
    }

    // ===== update() =====

    @Test
    fun `update success replaces files and DB record`() {
        val oldTwi = mockTestWithImages(1, "Old", "cover.jpg", listOf("000.jpg"))
        val newTwi = mockTestWithImages(1, "New", "cover.png", listOf("000.png", "001.png"))
        every { testDao.findById(1) } returns oldTwi
        every { testDao.update(1, "New", "cover.png", listOf("000.png", "001.png")) } returns newTwi

        // Create old files
        val dir = File(uploadDir, "tests/1").also { it.mkdirs() }
        File(dir, "cover.jpg").writeText("old-cover")
        File(dir, "000.jpg").writeText("old-img")

        val result = testService.update(
            1, "New", dummyStream("new-cover"), "png",
            listOf(dummyStream("img0") to "png", dummyStream("img1") to "png")
        )

        assertIs<TestResult.Success>(result)
        assertEquals("New", result.response.name)
        assertEquals(2, result.response.imageUrls.size)
        assertTrue(File(uploadDir, "tests/1/cover.png").exists())
        assertTrue(File(uploadDir, "tests/1/000.png").exists())
        assertTrue(File(uploadDir, "tests/1/001.png").exists())
        assertTrue(!File(uploadDir, "tests/1/cover.jpg").exists())
        assertTrue(!File(uploadDir, "tests/1/000.jpg").exists())
    }

    @Test
    fun `update fails with blank name`() {
        val result = testService.update(1, "   ", dummyStream(), "jpg", listOf(dummyStream() to "jpg"))
        assertIs<TestResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `update fails with empty images`() {
        val result = testService.update(1, "Valid", dummyStream(), "jpg", emptyList())
        assertIs<TestResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `update returns 404 when test not found`() {
        every { testDao.findById(999) } returns null
        val result = testService.update(999, "Name", dummyStream(), "jpg", listOf(dummyStream() to "jpg"))
        assertIs<TestResult.Error>(result)
        assertEquals(404, result.status)
    }

    @Test
    fun `update rolls back temp dir on IO failure`() {
        val twi = mockTestWithImages(1, "Old", "cover.jpg", listOf("000.jpg"))
        every { testDao.findById(1) } returns twi

        val badStream = mockk<InputStream>()
        every { badStream.read(any<ByteArray>()) } throws IOException("disk full")
        every { badStream.read(any<ByteArray>(), any(), any()) } throws IOException("disk full")
        every { badStream.close() } returns Unit

        val result = testService.update(1, "Fail", badStream, "jpg", listOf(dummyStream() to "jpg"))
        assertIs<TestResult.Error>(result)
        assertEquals(500, result.status)
        assertTrue(!File(uploadDir, "tests/1_tmp").exists())
    }

    // ===== getAll() =====

    @Test
    fun `getAll returns all tests`() {
        every { testDao.findAll() } returns listOf(
            mockTestWithImages(1, "T1", "c1.jpg", listOf("i1.jpg")),
            mockTestWithImages(2, "T2", "c2.jpg", listOf("i2.jpg")),
        )
        val result = testService.getAll()
        assertEquals(2, result.tests.size)
    }

    @Test
    fun `getAll returns empty list`() {
        every { testDao.findAll() } returns emptyList()
        val result = testService.getAll()
        assertTrue(result.tests.isEmpty())
    }

    // ===== getById() =====

    @Test
    fun `getById returns test when found`() {
        val twi = mockTestWithImages(1, "Found", "cover.jpg", listOf("img.jpg"))
        every { testDao.findById(1) } returns twi

        val result = testService.getById(1)
        assertIs<TestResult.Success>(result)
        assertEquals(1, result.response.id)
        assertEquals("Found", result.response.name)
    }

    @Test
    fun `getById returns error when not found`() {
        every { testDao.findById(999) } returns null
        val result = testService.getById(999)
        assertIs<TestResult.Error>(result)
        assertEquals(404, result.status)
    }

    // ===== delete() =====

    @Test
    fun `delete success removes files and DB record`() {
        val twi = mockTestWithImages(1, "Del", "cover.jpg", listOf("000.jpg"))
        every { testDao.findById(1) } returns twi
        every { testDao.deleteById(1) } returns true

        // Create files to verify deletion
        val dir = File(uploadDir, "tests/1").also { it.mkdirs() }
        File(dir, "cover.jpg").writeText("data")
        File(dir, "000.jpg").writeText("data")

        val result = testService.delete(1)
        assertIs<TestResult.Success>(result)
        verify { testDao.deleteById(1) }
        assertTrue(!dir.exists())
    }

    @Test
    fun `delete returns error when not found`() {
        every { testDao.findById(999) } returns null
        val result = testService.delete(999)
        assertIs<TestResult.Error>(result)
        assertEquals(404, result.status)
        verify(exactly = 0) { testDao.deleteById(any()) }
    }

    // ===== getCoverFile() =====

    @Test
    fun `getCoverFile returns file when exists`() {
        val twi = mockTestWithImages(1, "T", "cover.jpg", emptyList())
        every { testDao.findById(1) } returns twi
        val dir = File(uploadDir, "tests/1").also { it.mkdirs() }
        File(dir, "cover.jpg").writeText("cover")

        val file = testService.getCoverFile(1)
        assertNotNull(file)
        assertTrue(file.exists())
    }

    @Test
    fun `getCoverFile returns null when test not found`() {
        every { testDao.findById(999) } returns null
        assertNull(testService.getCoverFile(999))
    }

    @Test
    fun `getCoverFile returns null when file missing from disk`() {
        val twi = mockTestWithImages(1, "T", "cover.jpg", emptyList())
        every { testDao.findById(1) } returns twi
        assertNull(testService.getCoverFile(1))
    }

    // ===== getImageFile() =====

    @Test
    fun `getImageFile returns file at valid index`() {
        val twi = mockTestWithImages(1, "T", "cover.jpg", listOf("000.jpg", "001.jpg"))
        every { testDao.findById(1) } returns twi
        val dir = File(uploadDir, "tests/1").also { it.mkdirs() }
        File(dir, "000.jpg").writeText("img0")

        val file = testService.getImageFile(1, 0)
        assertNotNull(file)
        assertTrue(file.exists())
    }

    @Test
    fun `getImageFile returns null when test not found`() {
        every { testDao.findById(999) } returns null
        assertNull(testService.getImageFile(999, 0))
    }

    @Test
    fun `getImageFile returns null for negative index`() {
        val twi = mockTestWithImages(1, "T", "cover.jpg", listOf("000.jpg"))
        every { testDao.findById(1) } returns twi
        assertNull(testService.getImageFile(1, -1))
    }

    @Test
    fun `getImageFile returns null for index out of bounds`() {
        val twi = mockTestWithImages(1, "T", "cover.jpg", listOf("000.jpg"))
        every { testDao.findById(1) } returns twi
        assertNull(testService.getImageFile(1, 5))
    }

    @Test
    fun `getImageFile returns null when file missing from disk`() {
        val twi = mockTestWithImages(1, "T", "cover.jpg", listOf("000.jpg"))
        every { testDao.findById(1) } returns twi
        assertNull(testService.getImageFile(1, 0))
    }
}
