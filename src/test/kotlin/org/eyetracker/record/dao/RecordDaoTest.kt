package org.eyetracker.record.dao

import kotlinx.datetime.Instant
import org.eyetracker.auth.dao.UserDao
import org.eyetracker.base.DatabaseTestBase
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.test.dao.TestDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordDaoTest : DatabaseTestBase() {

    private val userDao = UserDao()
    private val testDao = TestDao()
    private val recordDao = RecordDao()
    private var testId: Int = 0
    private var imageIds: List<Int> = emptyList()

    @BeforeEach
    fun createTestData() {
        val user = userDao.createUser("owner@test.com", "hashed_pw")
        val twi = testDao.create("Test", "cover.jpg", listOf("000.jpg", "001.jpg"), user.id.value)
        testId = twi.test.id.value
        imageIds = twi.imageIds
    }

    private val startedAt = Instant.parse("2025-01-01T10:00:00Z")
    private val finishedAt = Instant.parse("2025-01-01T10:05:00Z")

    private fun createDefaultRecord(
        userLogin: String = "user@test.com",
        started: Instant = startedAt,
    ): RecordWithItems {
        val items = imageIds.map { CreateRecordItemData(it, RecordItemMetrics(1.5)) }
        return recordDao.create(testId, userLogin, started, finishedAt, 300000, items)
    }

    @Test
    fun `create returns record with items`() {
        val result = createDefaultRecord()
        assertEquals(testId, result.record.testId)
        assertEquals("user@test.com", result.record.userLogin)
        assertEquals(300000, result.record.durationMs)
        assertEquals(2, result.items.size)
        assertEquals(1.5, result.items[0].metrics.placeholderMetric)
    }

    @Test
    fun `create with single item`() {
        val items = listOf(CreateRecordItemData(imageIds[0], RecordItemMetrics(2.0)))
        val result = recordDao.create(testId, "user@test.com", startedAt, finishedAt, 100, items)
        assertEquals(1, result.items.size)
        assertEquals(2.0, result.items[0].metrics.placeholderMetric)
    }

    @Test
    fun `findById returns record with items when exists`() {
        val created = createDefaultRecord()
        val found = recordDao.findById(created.record.id.value)
        assertNotNull(found)
        assertEquals(created.record.id.value, found.record.id.value)
        assertEquals(2, found.items.size)
        assertEquals(1.5, found.items[0].metrics.placeholderMetric)
    }

    @Test
    fun `findById returns null when not exists`() {
        assertNull(recordDao.findById(99999))
    }

    @Test
    fun `findAll returns paginated results`() {
        repeat(5) { createDefaultRecord(userLogin = "user$it@test.com") }
        val (records, total) = recordDao.findAll(1, 2, null, null, null)
        assertEquals(2, records.size)
        assertEquals(5L, total)
    }

    @Test
    fun `findAll page 2 returns correct offset`() {
        repeat(5) { createDefaultRecord(userLogin = "user$it@test.com") }
        val (records, total) = recordDao.findAll(2, 2, null, null, null)
        assertEquals(2, records.size)
        assertEquals(5L, total)
    }

    @Test
    fun `findAll filters by userLogin`() {
        createDefaultRecord(userLogin = "alice@test.com")
        createDefaultRecord(userLogin = "bob@test.com")
        createDefaultRecord(userLogin = "alice@test.com")
        val (records, total) = recordDao.findAll(1, 10, "alice@test.com", null, null)
        assertEquals(2, records.size)
        assertEquals(2L, total)
    }

    @Test
    fun `findAll filters by from timestamp`() {
        createDefaultRecord(started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(started = Instant.parse("2025-01-01T12:00:00Z"))
        val (records, total) = recordDao.findAll(1, 10, null, Instant.parse("2025-01-01T10:00:00Z"), null)
        assertEquals(1, records.size)
        assertEquals(1L, total)
    }

    @Test
    fun `findAll filters by to timestamp`() {
        createDefaultRecord(started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(started = Instant.parse("2025-01-01T12:00:00Z"))
        val (records, total) = recordDao.findAll(1, 10, null, null, Instant.parse("2025-01-01T09:00:00Z"))
        assertEquals(1, records.size)
        assertEquals(1L, total)
    }

    @Test
    fun `findAll with combined filters`() {
        createDefaultRecord(userLogin = "alice@test.com", started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(userLogin = "alice@test.com", started = Instant.parse("2025-01-01T12:00:00Z"))
        createDefaultRecord(userLogin = "bob@test.com", started = Instant.parse("2025-01-01T08:00:00Z"))
        val (records, total) = recordDao.findAll(
            1, 10, "alice@test.com",
            Instant.parse("2025-01-01T07:00:00Z"),
            Instant.parse("2025-01-01T09:00:00Z"),
        )
        assertEquals(1, records.size)
        assertEquals(1L, total)
    }

    @Test
    fun `findAll returns empty when no matches`() {
        val (records, total) = recordDao.findAll(1, 10, "nobody@test.com", null, null)
        assertTrue(records.isEmpty())
        assertEquals(0L, total)
    }
}
