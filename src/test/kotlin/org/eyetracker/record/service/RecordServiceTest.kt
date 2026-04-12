package org.eyetracker.record.service

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import org.eyetracker.record.dao.RecordDao
import org.eyetracker.record.dao.RecordEntity
import org.eyetracker.record.dao.RecordTable
import org.eyetracker.record.dto.CreateRecordRequest
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.test.dao.TestDao
import org.eyetracker.test.dao.TestEntity
import org.eyetracker.test.dao.TestPassTokenDao
import org.eyetracker.test.dao.TestTable
import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordServiceTest {

    private lateinit var recordDao: RecordDao
    private lateinit var testDao: TestDao
    private lateinit var tokenDao: TestPassTokenDao
    private lateinit var recordService: RecordService

    @BeforeEach
    fun setup() {
        recordDao = mockk()
        testDao = mockk()
        tokenDao = mockk()
        recordService = RecordService(recordDao, testDao, tokenDao)
    }

    private fun mockTestEntity(id: Int, aoi: String? = null): TestEntity {
        val entity = mockk<TestEntity>()
        every { entity.id } returns EntityID(id, TestTable)
        every { entity.name } returns "Test"
        every { entity.aoi } returns aoi
        every { entity.userId } returns 1
        every { entity.createdAt } returns Clock.System.now()
        return entity
    }

    private fun mockRecordEntity(
        id: Int,
        testId: Int = 1,
        userLogin: String = "user@test.com",
        metricsJson: String = """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],"roiMetrics":[],"aoiSequence":[]}""",
    ): RecordEntity {
        val entity = mockk<RecordEntity>()
        every { entity.id } returns EntityID(id, RecordTable)
        every { entity.testId } returns testId
        every { entity.userLogin } returns userLogin
        every { entity.metricsJson } returns metricsJson
        every { entity.startedAt } returns Clock.System.now()
        every { entity.finishedAt } returns Clock.System.now()
        every { entity.durationMs } returns 300000
        every { entity.createdAt } returns Clock.System.now()
        return entity
    }

    private fun validRequest(testId: Int = 1) = CreateRecordRequest(
        testId = testId,
        startedAt = "2025-01-01T10:00:00Z",
        finishedAt = "2025-01-01T10:05:00Z",
        durationMs = 300000,
        metrics = RecordItemMetrics(),
    )

    // ===== create() =====

    @Test
    fun `create success`() {
        every { testDao.findById(1) } returns mockTestEntity(1)
        every { recordDao.create(any(), any(), any(), any(), any(), any()) } returns mockRecordEntity(1)

        val result = recordService.create(validRequest(), "user@test.com")
        assertIs<RecordResult.Success>(result)
        assertEquals(1, result.response.id)
    }

    @Test
    fun `create fails with negative duration`() {
        val result = recordService.create(validRequest().copy(durationMs = -1), "user@test.com")
        assertIs<RecordResult.Error>(result)
        assertEquals(400, result.status)
    }

    @Test
    fun `create fails with invalid timestamp`() {
        val result = recordService.create(validRequest().copy(startedAt = "not-a-timestamp"), "user@test.com")
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

    // ===== getById() =====

    @Test
    fun `getById returns success when found`() {
        every { recordDao.findById(1) } returns mockRecordEntity(1)
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
        every { recordDao.findAll(1, 20, null, null, null, null, null) } returns Pair(records, 5L)

        val result = recordService.getAll(1, 20, null, null, null, null, null)
        assertEquals(2, result.items.size)
        assertEquals(1, result.page)
        assertEquals(20, result.pageSize)
        assertEquals(5, result.total)
    }

    @Test
    fun `getAll clamps pageSize to max 100`() {
        every { recordDao.findAll(1, 100, null, null, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(1, 500, null, null, null, null, null)
        assertEquals(100, result.pageSize)
    }

    @Test
    fun `getAll clamps page to min 1`() {
        every { recordDao.findAll(1, 20, null, null, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(-5, 20, null, null, null, null, null)
        assertEquals(1, result.page)
    }

    @Test
    fun `getAll filters by testId`() {
        val records = listOf(mockRecordEntity(1, testId = 42))
        every { recordDao.findAll(1, 20, 42, null, null, null, null) } returns Pair(records, 1L)

        val result = recordService.getAll(1, 20, 42, null, null, null, null)
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

    // ===== getAll() with aoiFilter =====

    private val hitMetrics = """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],"roiMetrics":[{"name":"hasGaze","hit":true}],"aoiSequence":[]}"""
    private val missMetrics = """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],"roiMetrics":[{"name":"hasGaze","hit":false}],"aoiSequence":[]}"""
    private val emptyMetrics = """{"gazeGroups":[],"fixations":[],"firstFixationTimeMs":null,"saccades":[],"roiMetrics":[],"aoiSequence":[]}"""

    @Test
    fun `getAll with aoiFilter returns only records whose roiMetrics match`() {
        val record1 = mockRecordEntity(1, metricsJson = hitMetrics)
        val record2 = mockRecordEntity(2, metricsJson = missMetrics)
        every { recordDao.findAllUnpaginated(null, null, null, null, null) } returns listOf(record1, record2)

        val result = recordService.getAll(1, 20, null, null, null, null, null, aoiFilter = mapOf("hasGaze" to true))
        assertEquals(1, result.items.size)
        assertEquals(1, result.items[0].id)
        assertEquals(1, result.total)
    }

    @Test
    fun `getAll with aoiFilter excludes records with empty roiMetrics`() {
        val record1 = mockRecordEntity(1, metricsJson = emptyMetrics)
        every { recordDao.findAllUnpaginated(null, null, null, null, null) } returns listOf(record1)

        val result = recordService.getAll(1, 20, null, null, null, null, null, aoiFilter = mapOf("hasGaze" to true))
        assertEquals(0, result.items.size)
        assertEquals(0, result.total)
    }

    @Test
    fun `getAll with aoiFilter paginates correctly`() {
        val records = (1..5).map { mockRecordEntity(it, metricsJson = hitMetrics) }
        every { recordDao.findAllUnpaginated(null, null, null, null, null) } returns records

        val result = recordService.getAll(1, 2, null, null, null, null, null, aoiFilter = mapOf("hasGaze" to true))
        assertEquals(2, result.items.size)
        assertEquals(5, result.total)
        assertEquals(2, result.pageSize)
    }

    @Test
    fun `getAll with empty aoiFilter uses normal dao path`() {
        every { recordDao.findAll(1, 20, null, null, null, null, null) } returns Pair(emptyList(), 0L)
        val result = recordService.getAll(1, 20, null, null, null, null, null, aoiFilter = emptyMap())
        assertEquals(0, result.items.size)
    }

    // ===== syncAoiMetrics() =====

    @Test
    fun `syncAoiMetrics returns error when test not found`() {
        every { testDao.findById(999) } returns null

        val result = recordService.syncAoiMetrics(999)

        assertIs<RecordResult.Error>(result)
        assertEquals(404, result.status)
    }

    @Test
    fun `syncAoiMetrics returns success and calls updateMetrics for each record`() {
        every { testDao.findById(1) } returns mockTestEntity(1, aoi = null)
        every { recordDao.findAllForTest(1) } returns listOf(mockRecordEntity(10), mockRecordEntity(11))
        justRun { recordDao.updateMetrics(any(), any()) }

        val result = recordService.syncAoiMetrics(1)

        assertIs<RecordResult.Success>(result)
        verify(exactly = 2) { recordDao.updateMetrics(any(), any()) }
    }

    @Test
    fun `syncAoiMetrics handles malformed metricsJson and still updates the record`() {
        every { testDao.findById(1) } returns mockTestEntity(1, aoi = null)
        every { recordDao.findAllForTest(1) } returns listOf(mockRecordEntity(10, metricsJson = "invalid-json"))
        justRun { recordDao.updateMetrics(any(), any()) }

        val result = recordService.syncAoiMetrics(1)

        assertIs<RecordResult.Success>(result)
        verify(exactly = 1) { recordDao.updateMetrics(any(), any()) }
    }

    @Test
    fun `syncAoiMetrics with no records returns success without calling updateMetrics`() {
        every { testDao.findById(1) } returns mockTestEntity(1, aoi = null)
        every { recordDao.findAllForTest(1) } returns emptyList()

        val result = recordService.syncAoiMetrics(1)

        assertIs<RecordResult.Success>(result)
        verify(exactly = 0) { recordDao.updateMetrics(any(), any()) }
    }
}
