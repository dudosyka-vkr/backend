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
        val (records, total) = recordDao.findAll(1, 2, null, null, null, null)
        assertEquals(2, records.size)
        assertEquals(5L, total)
    }

    @Test
    fun `findAll page 2 returns correct offset`() {
        repeat(5) { createDefaultRecord(userLogin = "user$it@test.com") }
        val (records, total) = recordDao.findAll(2, 2, null, null, null, null)
        assertEquals(2, records.size)
        assertEquals(5L, total)
    }

    @Test
    fun `findAll filters by userLogin`() {
        createDefaultRecord(userLogin = "alice@test.com")
        createDefaultRecord(userLogin = "bob@test.com")
        createDefaultRecord(userLogin = "alice@test.com")
        val (records, total) = recordDao.findAll(1, 10, null, "alice@test.com", null, null)
        assertEquals(2, records.size)
        assertEquals(2L, total)
    }

    @Test
    fun `findAll filters by from timestamp`() {
        createDefaultRecord(started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(started = Instant.parse("2025-01-01T12:00:00Z"))
        val (records, total) = recordDao.findAll(1, 10, null, null, Instant.parse("2025-01-01T10:00:00Z"), null)
        assertEquals(1, records.size)
        assertEquals(1L, total)
    }

    @Test
    fun `findAll filters by to timestamp`() {
        createDefaultRecord(started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(started = Instant.parse("2025-01-01T12:00:00Z"))
        val (records, total) = recordDao.findAll(1, 10, null, null, null, Instant.parse("2025-01-01T09:00:00Z"))
        assertEquals(1, records.size)
        assertEquals(1L, total)
    }

    @Test
    fun `findAll with combined filters`() {
        createDefaultRecord(userLogin = "alice@test.com", started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(userLogin = "alice@test.com", started = Instant.parse("2025-01-01T12:00:00Z"))
        createDefaultRecord(userLogin = "bob@test.com", started = Instant.parse("2025-01-01T08:00:00Z"))
        val (records, total) = recordDao.findAll(
            1, 10, null, "alice@test.com",
            Instant.parse("2025-01-01T07:00:00Z"),
            Instant.parse("2025-01-01T09:00:00Z"),
        )
        assertEquals(1, records.size)
        assertEquals(1L, total)
    }

    @Test
    fun `findAll filters by testId`() {
        val user2 = userDao.createUser("owner2@test.com", "hashed_pw")
        val twi2 = testDao.create("Test2", "cover2.jpg", listOf("000.jpg"), user2.id.value)
        val testId2 = twi2.test.id.value
        val imageIds2 = twi2.imageIds

        createDefaultRecord()
        val items2 = imageIds2.map { CreateRecordItemData(it, RecordItemMetrics(1.0)) }
        recordDao.create(testId2, "user@test.com", startedAt, finishedAt, 100, items2)

        val (records, total) = recordDao.findAll(1, 10, testId, null, null, null)
        assertEquals(1, records.size)
        assertEquals(1L, total)
        assertEquals(testId, records[0].testId)
    }

    @Test
    fun `suggestUsers returns distinct logins`() {
        createDefaultRecord(userLogin = "alice@test.com")
        createDefaultRecord(userLogin = "alice@test.com")
        createDefaultRecord(userLogin = "bob@test.com")

        val (logins, total) = recordDao.suggestUsers(1, 10, null, null, null)
        assertEquals(2, logins.size)
        assertEquals(2L, total)
        assertTrue(logins.contains("alice@test.com"))
        assertTrue(logins.contains("bob@test.com"))
    }

    @Test
    fun `suggestUsers filters by testId`() {
        val user2 = userDao.createUser("owner2@test.com", "hashed_pw")
        val twi2 = testDao.create("Test2", "cover2.jpg", listOf("000.jpg"), user2.id.value)
        val testId2 = twi2.test.id.value
        val imageIds2 = twi2.imageIds
        val items2 = imageIds2.map { CreateRecordItemData(it, RecordItemMetrics(1.0)) }

        createDefaultRecord(userLogin = "alice@test.com")
        recordDao.create(testId2, "bob@test.com", startedAt, finishedAt, 100, items2)

        val (logins, total) = recordDao.suggestUsers(1, 10, testId, null, null)
        assertEquals(1, logins.size)
        assertEquals("alice@test.com", logins[0])
        assertEquals(1L, total)
    }

    @Test
    fun `suggestUsers filters by from timestamp`() {
        createDefaultRecord(userLogin = "early@test.com", started = Instant.parse("2025-01-01T08:00:00Z"))
        createDefaultRecord(userLogin = "late@test.com", started = Instant.parse("2025-01-01T12:00:00Z"))

        val (logins, total) = recordDao.suggestUsers(1, 10, null, Instant.parse("2025-01-01T10:00:00Z"), null)
        assertEquals(1, logins.size)
        assertEquals("late@test.com", logins[0])
        assertEquals(1L, total)
    }

    @Test
    fun `suggestUsers returns empty when no matches`() {
        val (logins, total) = recordDao.suggestUsers(1, 10, 99999, null, null)
        assertTrue(logins.isEmpty())
        assertEquals(0L, total)
    }

    @Test
    fun `findAll returns empty when no matches`() {
        val (records, total) = recordDao.findAll(1, 10, null, "nobody@test.com", null, null)
        assertTrue(records.isEmpty())
        assertEquals(0L, total)
    }

    // ===== findAllUnpaginated() =====

    @Test
    fun `findAllUnpaginated returns all matching records`() {
        repeat(5) { createDefaultRecord(userLogin = "user$it@test.com") }
        val records = recordDao.findAllUnpaginated(null, null, null, null)
        assertEquals(5, records.size)
    }

    @Test
    fun `findAllUnpaginated filters by userLogin`() {
        createDefaultRecord(userLogin = "alice@test.com")
        createDefaultRecord(userLogin = "bob@test.com")
        val records = recordDao.findAllUnpaginated(null, "alice@test.com", null, null)
        assertEquals(1, records.size)
        assertEquals("alice@test.com", records[0].userLogin)
    }

    @Test
    fun `findAllUnpaginated filters by testId`() {
        val user2 = userDao.createUser("owner2@test.com", "hashed_pw")
        val twi2 = testDao.create("Test2", "cover2.jpg", listOf("000.jpg"), user2.id.value)
        val testId2 = twi2.test.id.value
        val imageIds2 = twi2.imageIds
        createDefaultRecord()
        val items2 = imageIds2.map { CreateRecordItemData(it, RecordItemMetrics(1.0)) }
        recordDao.create(testId2, "user@test.com", startedAt, finishedAt, 100, items2)

        val records = recordDao.findAllUnpaginated(testId, null, null, null)
        assertEquals(1, records.size)
        assertEquals(testId, records[0].testId)
    }

    @Test
    fun `findAllUnpaginated returns empty when no matches`() {
        val records = recordDao.findAllUnpaginated(99999, null, null, null)
        assertTrue(records.isEmpty())
    }

    // ===== findImageRoisForRecords() =====

    @Test
    fun `findImageRoisForRecords returns empty map for empty input`() {
        val rois = recordDao.findImageRoisForRecords(emptyList())
        assertTrue(rois.isEmpty())
    }

    @Test
    fun `findImageRoisForRecords returns null rois when not set`() {
        val record = createDefaultRecord()
        val rois = recordDao.findImageRoisForRecords(listOf(record.record.id.value))
        val recordRois = rois[record.record.id.value]
        assertNotNull(recordRois)
        assertTrue(recordRois.all { it == null })
    }

    @Test
    fun `findImageRoisForRecords returns roi value when set`() {
        testDao.updateImageRoi(imageIds[0], """{"hasGaze":true}""")
        val record = createDefaultRecord()
        val rois = recordDao.findImageRoisForRecords(listOf(record.record.id.value))
        val recordRois = rois[record.record.id.value]
        assertNotNull(recordRois)
        assertTrue(recordRois.any { it == """{"hasGaze":true}""" })
    }

    @Test
    fun `findImageRoisForRecords groups rois by record`() {
        testDao.updateImageRoi(imageIds[0], """{"hasGaze":true}""")
        val record1 = createDefaultRecord(userLogin = "a@test.com")
        val record2 = createDefaultRecord(userLogin = "b@test.com")
        val rois = recordDao.findImageRoisForRecords(listOf(record1.record.id.value, record2.record.id.value))
        assertEquals(2, rois.size)
        assertTrue(rois[record1.record.id.value]!!.any { it == """{"hasGaze":true}""" })
        assertTrue(rois[record2.record.id.value]!!.any { it == """{"hasGaze":true}""" })
    }
}
