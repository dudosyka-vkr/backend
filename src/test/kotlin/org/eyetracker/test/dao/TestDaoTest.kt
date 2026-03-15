package org.eyetracker.test.dao

import org.eyetracker.auth.dao.UserDao
import org.eyetracker.base.DatabaseTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestDaoTest : DatabaseTestBase() {

    private val userDao = UserDao()
    private val testDao = TestDao()
    private var userId: Int = 0

    @BeforeEach
    fun createTestUser() {
        val user = userDao.createUser("owner@test.com", "hashed_pw")
        userId = user.id.value
    }

    @Test
    fun `create returns test with images`() {
        val result = testDao.create("Test 1", "cover.jpg", listOf("000.jpg", "001.jpg"), userId)
        assertEquals("Test 1", result.test.name)
        assertEquals("cover.jpg", result.test.coverFilename)
        assertEquals(userId, result.test.userId)
        assertNotNull(result.test.createdAt)
        assertEquals(listOf("000.jpg", "001.jpg"), result.imageFilenames)
    }

    @Test
    fun `create with single image`() {
        val result = testDao.create("Single", "cover.jpg", listOf("000.jpg"), userId)
        assertEquals(1, result.imageFilenames.size)
    }

    @Test
    fun `create preserves image sort order`() {
        val result = testDao.create("Ordered", "cover.jpg", listOf("c.jpg", "a.jpg", "b.jpg"), userId)
        assertEquals(listOf("c.jpg", "a.jpg", "b.jpg"), result.imageFilenames)
    }

    @Test
    fun `findAll returns all tests`() {
        testDao.create("Test 1", "cover1.jpg", listOf("img1.jpg"), userId)
        testDao.create("Test 2", "cover2.jpg", listOf("img2.jpg"), userId)
        testDao.create("Test 3", "cover3.jpg", listOf("img3.jpg"), userId)
        val all = testDao.findAll()
        assertEquals(3, all.size)
    }

    @Test
    fun `findAll returns empty list when no tests`() {
        val all = testDao.findAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `findAll returns images sorted within each test`() {
        testDao.create("Test", "cover.jpg", listOf("z.jpg", "a.jpg", "m.jpg"), userId)
        val all = testDao.findAll()
        assertEquals(listOf("z.jpg", "a.jpg", "m.jpg"), all[0].imageFilenames)
    }

    @Test
    fun `findById returns test when exists`() {
        val created = testDao.create("Found", "cover.jpg", listOf("img.jpg"), userId)
        val id = created.test.id.value
        val found = testDao.findById(id)
        assertNotNull(found)
        assertEquals("Found", found.test.name)
        assertEquals("cover.jpg", found.test.coverFilename)
        assertEquals(listOf("img.jpg"), found.imageFilenames)
    }

    @Test
    fun `findById returns null when not exists`() {
        assertNull(testDao.findById(99999))
    }

    @Test
    fun `update changes name cover and images`() {
        val created = testDao.create("Original", "cover.jpg", listOf("000.jpg"), userId)
        val id = created.test.id.value

        val updated = testDao.update(id, "Updated", "cover.png", listOf("000.png", "001.png"))
        assertNotNull(updated)
        assertEquals("Updated", updated.test.name)
        assertEquals("cover.png", updated.test.coverFilename)
        assertEquals(listOf("000.png", "001.png"), updated.imageFilenames)

        val found = testDao.findById(id)
        assertNotNull(found)
        assertEquals("Updated", found.test.name)
        assertEquals(listOf("000.png", "001.png"), found.imageFilenames)
    }

    @Test
    fun `update returns null for nonexistent test`() {
        assertNull(testDao.update(99999, "Nope", "cover.jpg", listOf("000.jpg")))
    }

    @Test
    fun `update replaces all images`() {
        val created = testDao.create("Test", "cover.jpg", listOf("a.jpg", "b.jpg", "c.jpg"), userId)
        val id = created.test.id.value

        val updated = testDao.update(id, "Test", "cover.jpg", listOf("x.jpg"))
        assertNotNull(updated)
        assertEquals(1, updated.imageFilenames.size)
        assertEquals("x.jpg", updated.imageFilenames[0])
    }

    @Test
    fun `deleteById deletes test and images`() {
        val created = testDao.create("ToDelete", "cover.jpg", listOf("a.jpg", "b.jpg"), userId)
        val id = created.test.id.value
        val deleted = testDao.deleteById(id)
        assertTrue(deleted)
        assertNull(testDao.findById(id))
    }

    @Test
    fun `deleteById returns false when not exists`() {
        assertFalse(testDao.deleteById(99999))
    }
}
