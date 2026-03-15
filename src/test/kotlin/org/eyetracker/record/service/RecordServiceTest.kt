package org.eyetracker.record.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import org.eyetracker.record.dao.CreateRecordItemData
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dao.RecordEntity
import org.eyetracker.record.dao.RecordItemWithMetrics
import org.eyetracker.record.dao.RecordTable
import org.eyetracker.record.dao.RecordWithItems
import org.eyetracker.record.dto.CreateRecordItemRequest
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.record.dto.RecordSummaryResponse
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestEntity
import org.eyetracker.test.dao.TestTable
import org.eyetracker.test.dao.TestWithImages
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        userLogin: String = "user@test.com",
        items: List<CreateRecordItemRequest> = listOf(
            CreateRecordItemRequest(10, RecordItemMetrics(1.5)),
        ),
    ) = CreateRecordRequest(
        testId = testId,
        userLogin = userLogin,
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

        val result = recordService.create(validRequest())
        assertIs<RecordResult.Success>(result)
        assertEquals(1, result.response.id)
        assertEquals(1, result.response.items.size)
    }

    @Test
    fun `create fails with blank userLogin`() {
        val result = recordService.create(validRequest(userLogin = "   "))
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
        verify(exactly = 0) { recordDao.create(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `create fails with empty items`() {
        val result = recordService.create(validRequest(items = emptyList()))
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails with negative duration`() {
        val req = validRequest().copy(durationMs = -1)
        val result = recordService.create(req)
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails with invalid timestamp`() {
        val req = validRequest().copy(startedAt = "not-a-timestamp")
        val result = recordService.create(req)
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails when test not found`() {
        every { testDao.findById(999) } returns null
        val result = recordService.create(validRequest(testId = 999))
        assertIs<RecordResult.Error>(result)
        assertEquals(404, result.status)
    }

    @Test
    fun `create fails when imageId does not belong to test`() {
        val twi = TestWithImages(mockTestEntity(1), listOf("000.jpg"), listOf(10))
        every { testDao.findById(1) } returns twi
        every { testDao.findImageIdsByTestId(1) } returns listOf(10)

        val req = validRequest(items = listOf(CreateRecordItemRequest(999, RecordItemMetrics(1.0))))
        val result = recordService.create(req)
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
        every { recordDao.findAll(1, 20, null, null, null) } returns Pair(records, 5L)

        val result = recordService.getAll(1, 20, null, null, null)
        assertEquals(2, result.items.size)
        assertEquals(1, result.page)
        assertEquals(20, result.pageSize)
        assertEquals(5, result.total)
    }

    @Test
    fun `getAll clamps pageSize to max 100`() {
        every { recordDao.findAll(1, 100, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(1, 500, null, null, null)
        assertEquals(100, result.pageSize)
    }

    @Test
    fun `getAll clamps page to min 1`() {
        every { recordDao.findAll(1, 20, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(-5, 20, null, null, null)
        assertEquals(1, result.page)
    }
}
