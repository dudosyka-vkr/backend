package org.eyetracker.record.dao

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eyetracker.record.dto.RecordItemMetrics
import org.eyetracker.test.dao.TestImageTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

private val lenientJson = Json { ignoreUnknownKeys = true }

data class CreateRecordItemData(val imageId: Int, val metrics: RecordItemMetrics)

data class RecordWithItems(
    val record: RecordEntity,
    val items: List<RecordItemWithMetrics>,
)

data class RecordItemWithMetrics(
    val id: Int,
    val imageId: Int,
    val metrics: RecordItemMetrics,
)

data class RecordItemSyncData(
    val itemId: Int,
    val imageId: Int,
    val metricsJson: String,
)

data class RecordItemBriefData(
    val sortOrder: Int,
    val metricsJson: String,
)

class RecordDao {
    fun create(
        testId: Int,
        userLogin: String,
        startedAt: Instant,
        finishedAt: Instant,
        durationMs: Long,
        items: List<CreateRecordItemData>,
    ): RecordWithItems = transaction {
        val record = RecordEntity.new {
            this.testId = testId
            this.userLogin = userLogin
            this.startedAt = startedAt
            this.finishedAt = finishedAt
            this.durationMs = durationMs
            this.createdAt = Clock.System.now()
        }
        val resultItems = items.map { item ->
            val entity = RecordItemEntity.new {
                this.recordId = record.id.value
                this.imageId = item.imageId
                this.metricsJson = Json.encodeToString(item.metrics)
            }
            RecordItemWithMetrics(entity.id.value, item.imageId, item.metrics)
        }
        RecordWithItems(record, resultItems)
    }

    fun findById(recordId: Int): RecordWithItems? = transaction {
        val record = RecordEntity.findById(recordId) ?: return@transaction null
        val items = RecordItemEntity.find { RecordItemTable.recordId eq recordId }
            .map { entity ->
                val metrics = lenientJson.decodeFromString<RecordItemMetrics>(entity.metricsJson)
                RecordItemWithMetrics(entity.id.value, entity.imageId, metrics)
            }
        RecordWithItems(record, items)
    }

    fun findAll(
        page: Int,
        pageSize: Int,
        testId: Int?,
        userLogin: String?,
        userLoginContains: String?,
        from: Instant?,
        to: Instant?,
    ): Pair<List<RecordEntity>, Long> = transaction {
        val query = RecordTable.selectAll()
        if (testId != null) {
            query.andWhere { RecordTable.testId eq testId }
        }
        if (!userLogin.isNullOrBlank()) {
            query.andWhere { RecordTable.userLogin eq userLogin }
        }
        if (!userLoginContains.isNullOrBlank()) {
            query.andWhere { RecordTable.userLogin.lowerCase() like "%${userLoginContains.lowercase()}%" }
        }
        if (from != null) {
            query.andWhere { RecordTable.startedAt greaterEq from }
        }
        if (to != null) {
            query.andWhere { RecordTable.startedAt lessEq to }
        }
        val total = query.count()
        query.orderBy(RecordTable.id, SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
        val records = RecordEntity.wrapRows(query).toList()
        Pair(records, total)
    }

    fun findAllUnpaginated(
        testId: Int?,
        userLogin: String?,
        userLoginContains: String?,
        from: Instant?,
        to: Instant?,
    ): List<RecordEntity> = transaction {
        val query = RecordTable.selectAll()
        if (testId != null) query.andWhere { RecordTable.testId eq testId }
        if (!userLogin.isNullOrBlank()) query.andWhere { RecordTable.userLogin eq userLogin }
        if (!userLoginContains.isNullOrBlank()) {
            query.andWhere { RecordTable.userLogin.lowerCase() like "%${userLoginContains.lowercase()}%" }
        }
        if (from != null) query.andWhere { RecordTable.startedAt greaterEq from }
        if (to != null) query.andWhere { RecordTable.startedAt lessEq to }
        query.orderBy(RecordTable.id, SortOrder.DESC)
        RecordEntity.wrapRows(query).toList()
    }

    fun findItemsBriefForRecords(recordIds: List<Int>): Map<Int, List<RecordItemBriefData>> = transaction {
        if (recordIds.isEmpty()) return@transaction emptyMap()
        (RecordItemTable innerJoin TestImageTable)
            .select(RecordItemTable.recordId, TestImageTable.sortOrder, RecordItemTable.metricsJson)
            .where { RecordItemTable.recordId inList recordIds }
            .groupBy { it[RecordItemTable.recordId] }
            .mapValues { (_, rows) ->
                rows.map { RecordItemBriefData(it[TestImageTable.sortOrder], it[RecordItemTable.metricsJson]) }
            }
    }

    fun findMetricsJsonForRecords(recordIds: List<Int>): Map<Int, List<String>> = transaction {
        if (recordIds.isEmpty()) return@transaction emptyMap()
        RecordItemTable
            .select(RecordItemTable.recordId, RecordItemTable.metricsJson)
            .where { RecordItemTable.recordId inList recordIds }
            .groupBy { it[RecordItemTable.recordId] }
            .mapValues { (_, rows) -> rows.map { it[RecordItemTable.metricsJson] } }
    }

    fun suggestUsers(
        page: Int,
        pageSize: Int,
        testId: Int?,
        from: Instant?,
        to: Instant?,
    ): Pair<List<String>, Long> = transaction {
        val query = RecordTable.select(RecordTable.userLogin).withDistinct()
        if (testId != null) {
            query.andWhere { RecordTable.testId eq testId }
        }
        if (from != null) {
            query.andWhere { RecordTable.startedAt greaterEq from }
        }
        if (to != null) {
            query.andWhere { RecordTable.startedAt lessEq to }
        }
        val total = query.count()
        query.orderBy(RecordTable.userLogin, SortOrder.ASC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
        val logins = query.map { it[RecordTable.userLogin] }
        Pair(logins, total)
    }

    fun findItemsForTest(testId: Int): List<RecordItemSyncData> = transaction {
        (RecordItemTable innerJoin RecordTable)
            .select(RecordItemTable.id, RecordItemTable.imageId, RecordItemTable.metricsJson)
            .where { RecordTable.testId eq testId }
            .map {
                RecordItemSyncData(
                    itemId = it[RecordItemTable.id].value,
                    imageId = it[RecordItemTable.imageId],
                    metricsJson = it[RecordItemTable.metricsJson],
                )
            }
    }

    data class RecordItemStatsData(
        val recordId: Int,
        val userLogin: String,
        val metricsJson: String,
    )

    fun findItemsWithRecordForTest(testId: Int): List<RecordItemStatsData> = transaction {
        (RecordItemTable innerJoin RecordTable)
            .select(RecordTable.id, RecordTable.userLogin, RecordItemTable.metricsJson)
            .where { RecordTable.testId eq testId }
            .map {
                RecordItemStatsData(
                    recordId = it[RecordTable.id].value,
                    userLogin = it[RecordTable.userLogin],
                    metricsJson = it[RecordItemTable.metricsJson],
                )
            }
    }

    fun updateItemMetrics(itemId: Int, metricsJson: String): Unit = transaction {
        RecordItemTable.update({ RecordItemTable.id eq itemId }) {
            it[RecordItemTable.metricsJson] = metricsJson
        }
    }
}
