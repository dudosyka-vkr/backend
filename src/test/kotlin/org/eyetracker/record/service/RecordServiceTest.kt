package org.eyetracker.record.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Clock
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dao.RecordEntity
import org.eyetracker.record.dao.RecordItemWithMetrics
import org.eyetracker.record.dao.RecordTable
import org.eyetracker.record.dao.RecordWithItems
import org.eyetracker.record.dto.CreateRecordItemRequest
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestEntity
import org.eyetracker.test.dao.TestTable
import org.eyetracker.test.dao.TestWithImages
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordServiceTest {

    private lateinit var recordDao: RecordDao
    private lateinit var testDao: TestDao
    private lateinit var recordService: RecordService

    @BeforeEach
    fun setup() {
        recordDao = mockk()
        testDao = mockk()
        recordService = RecordService(recordDao, testDao)
    }

    private fun mockTestEntity(id: Int): TestEntity {
        val entity = mockk<TestEntity>()
        every { entity.id } returns EntityID(id, TestTable)
        every { entity.name } returns "Test"
        every { entity.coverFilename } returns "cover.jpg"
        every { entity.userId } returns 1
        every { entity.createdAt } returns Clock.System.now()
        return entity
    }

    private fun mockRecordEntity(id: Int, testId: Int = 1, userLogin: String = "user@test.com"): RecordEntity {
        val entity = mockk<RecordEntity>()
        every { entity.id } returns EntityID(id, RecordTable)
        every { entity.testId } returns testId
        every { entity.userLogin } returns userLogin
        every { entity.startedAt } returns Clock.System.now()
        every { entity.finishedAt } returns Clock.System.now()
        every { entity.durationMs } returns 300000
        every { entity.createdAt } returns Clock.System.now()
        return entity
    }

    private fun validRequest(
        testId: Int = 1,
        items: List<CreateRecordItemRequest> = listOf(
            CreateRecordItemRequest(10, RecordItemMetrics(1.5)),
        ),
    ) = CreateRecordRequest(
        testId = testId,
        startedAt = "2025-01-01T10:00:00Z",
        finishedAt = "2025-01-01T10:05:00Z",
        durationMs = 300000,
        items = items,
    )

    // ===== create() =====

    @Test
    fun `create success`() {
        val twi = TestWithImages(mockTestEntity(1), listOf("000.jpg"), listOf(10))
        every { testDao.findById(1) } returns twi
        every { testDao.findImageIdsByTestId(1) } returns listOf(10)

        val rwi = RecordWithItems(
            mockRecordEntity(1),
            listOf(RecordItemWithMetrics(100, 10, RecordItemMetrics(1.5))),
        )
        every { recordDao.create(any(), any(), any(), any(), any(), any()) } returns rwi

        val result = recordService.create(validRequest(), "user@test.com")
        assertIs<RecordResult.Success>(result)
        assertEquals(1, result.response.id)
        assertEquals(1, result.response.items.size)
    }

    @Test
    fun `create fails with empty items`() {
        val result = recordService.create(validRequest(items = emptyList()), "user@test.com")
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails with negative duration`() {
        val req = validRequest().copy(durationMs = -1)
        val result = recordService.create(req, "user@test.com")
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails with invalid timestamp`() {
        val req = validRequest().copy(startedAt = "not-a-timestamp")
        val result = recordService.create(req, "user@test.com")
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails when test not found`() {
        every { testDao.findById(999) } returns null
        val result = recordService.create(validRequest(testId = 999), "user@test.com")
        assertIs<RecordResult.Error>(result)
        assertEquals(404, result.status)
    }

    @Test
    fun `create fails when imageId does not belong to test`() {
        val twi = TestWithImages(mockTestEntity(1), listOf("000.jpg"), listOf(10))
        every { testDao.findById(1) } returns twi
        every { testDao.findImageIdsByTestId(1) } returns listOf(10)

        val req = validRequest(items = listOf(CreateRecordItemRequest(999, RecordItemMetrics(1.0))))
        val result = recordService.create(req, "user@test.com")
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    // ===== getById() =====

    @Test
    fun `getById returns success when found`() {
        val rwi = RecordWithItems(
            mockRecordEntity(1),
            listOf(RecordItemWithMetrics(100, 10, RecordItemMetrics(1.5))),
        )
        every { recordDao.findById(1) } returns rwi

        val result = recordService.getById(1)
        assertIs<RecordResult.Success>(result)
        assertEquals(1, result.response.id)
    }

    @Test
    fun `getById returns error when not found`() {
        every { recordDao.findById(999) } returns null
        val result = recordService.getById(999)
        assertIs<RecordResult.Error>(result)
        assertEquals(404, result.status)
    }

    // ===== getAll() =====

    @Test
    fun `getAll returns paginated response`() {
        val records = listOf(mockRecordEntity(1), mockRecordEntity(2))
        every { recordDao.findAll(1, 20, null, null, null, null) } returns Pair(records, 5L)

        val result = recordService.getAll(1, 20, null, null, null, null)
        assertEquals(2, result.items.size)
        assertEquals(1, result.page)
        assertEquals(20, result.pageSize)
        assertEquals(5, result.total)
    }

    @Test
    fun `getAll clamps pageSize to max 100`() {
        every { recordDao.findAll(1, 100, null, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(1, 500, null, null, null, null)
        assertEquals(100, result.pageSize)
    }

    @Test
    fun `getAll clamps page to min 1`() {
        every { recordDao.findAll(1, 20, null, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(-5, 20, null, null, null, null)
        assertEquals(1, result.page)
    }

    @Test
    fun `getAll filters by testId`() {
        val records = listOf(mockRecordEntity(1, testId = 42))
        every { recordDao.findAll(1, 20, 42, null, null, null) } returns Pair(records, 1L)

        val result = recordService.getAll(1, 20, 42, null, null, null)
        assertEquals(1, result.items.size)
        assertEquals(42, result.items[0].testId)
    }

    // ===== suggestUsers() =====

    @Test
    fun `suggestUsers returns distinct logins`() {
        every { recordDao.suggestUsers(1, 20, null, null, null) } returns Pair(listOf("alice@test.com", "bob@test.com"), 2L)

        val result = recordService.suggestUsers(1, 20, null, null, null)
        assertEquals(2, result.items.size)
        assertEquals(2, result.total)
        assertTrue(result.items.contains("alice@test.com"))
    }

    @Test
    fun `suggestUsers clamps pageSize`() {
        every { recordDao.suggestUsers(1, 100, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.suggestUsers(1, 999, null, null, null)
        assertEquals(100, result.pageSize)
    }

    @Test
    fun `suggestUsers filters by testId`() {
        every { recordDao.suggestUsers(1, 20, 5, null, null) } returns Pair(listOf("alice@test.com"), 1L)
        val result = recordService.suggestUsers(1, 20, 5, null, null)
        assertEquals(1, result.items.size)
        assertEquals("alice@test.com", result.items[0])
    }

    // ===== getAll() with roiFilter =====

    @Test
    fun `getAll with roiFilter returns only records whose images match`() {
        val record1 = mockRecordEntity(1)
        val record2 = mockRecordEntity(2)
        every { recordDao.findAllUnpaginated(null, null, null, null) } returns listOf(record1, record2)
        every { recordDao.findImageRoisForRecords(any()) } returns mapOf(
            1 to listOf("""{"hasGaze":true}"""),
            2 to listOf("""{"hasGaze":false}"""),
        )

        val result = recordService.getAll(1, 20, null, null, null, null, roiFilter = mapOf("hasGaze" to true))
        assertEquals(1, result.items.size)
        assertEquals(1, result.items[0].id)
        assertEquals(1, result.total)
    }

    @Test
    fun `getAll with roiFilter excludes records with null roi`() {
        val record1 = mockRecordEntity(1)
        every { recordDao.findAllUnpaginated(null, null, null, null) } returns listOf(record1)
        every { recordDao.findImageRoisForRecords(any()) } returns mapOf(1 to listOf(null))

        val result = recordService.getAll(1, 20, null, null, null, null, roiFilter = mapOf("hasGaze" to true))
        assertEquals(0, result.items.size)
        assertEquals(0, result.total)
    }

    @Test
    fun `getAll with roiFilter paginates correctly`() {
        val records = (1..5).map { mockRecordEntity(it) }
        every { recordDao.findAllUnpaginated(null, null, null, null) } returns records
        every { recordDao.findImageRoisForRecords(any()) } returns
            records.associate { it.id.value to listOf("""{"hasGaze":true}""") }

        val result = recordService.getAll(1, 2, null, null, null, null, roiFilter = mapOf("hasGaze" to true))
        assertEquals(2, result.items.size)
        assertEquals(5, result.total)
        assertEquals(2, result.pageSize)
    }

    @Test
    fun `getAll with empty roiFilter uses normal dao path`() {
        every { recordDao.findAll(1, 20, null, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(1, 20, null, null, null, null, roiFilter = emptyMap())
        assertEquals(0, result.items.size)
    }

    // ===== suggestUsers() with roiFilter =====

    @Test
    fun `suggestUsers with roiFilter returns logins for matching records`() {
        val record1 = mockRecordEntity(1, userLogin = "alice@test.com")
        val record2 = mockRecordEntity(2, userLogin = "bob@test.com")
        every { recordDao.findAllUnpaginated(null, null, null, null) } returns listOf(record1, record2)
        every { recordDao.findImageRoisForRecords(any()) } returns mapOf(
            1 to listOf("""{"hasGaze":true}"""),
            2 to listOf("""{"hasGaze":false}"""),
        )

        val result = recordService.suggestUsers(1, 20, null, null, null, roiFilter = mapOf("hasGaze" to true))
        assertEquals(1, result.items.size)
        assertEquals("alice@test.com", result.items[0])
        assertEquals(1, result.total)
    }

    @Test
    fun `suggestUsers with roiFilter deduplicates logins`() {
        val record1 = mockRecordEntity(1, userLogin = "alice@test.com")
        val record2 = mockRecordEntity(2, userLogin = "alice@test.com")
        every { recordDao.findAllUnpaginated(null, null, null, null) } returns listOf(record1, record2)
        every { recordDao.findImageRoisForRecords(any()) } returns mapOf(
            1 to listOf("""{"hasGaze":true}"""),
            2 to listOf("""{"hasGaze":true}"""),
        )

        val result = recordService.suggestUsers(1, 20, null, null, null, roiFilter = mapOf("hasGaze" to true))
        assertEquals(1, result.items.size)
        assertEquals(1, result.total)
    }

    @Test
    fun `suggestUsers with empty roiFilter uses normal dao path`() {
        every { recordDao.suggestUsers(1, 20, null, null, null) } returns Pair(listOf("alice@test.com"), 1L)
        val result = recordService.suggestUsers(1, 20, null, null, null, roiFilter = emptyMap())
        assertEquals(1, result.items.size)
    }
}
