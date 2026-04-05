package org.eyetracker.record.dao

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eyetracker.record.dto.RecordItemMetrics
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Slice
import org.jetbrains.exposed.sql.select

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
                val metrics = Json.decodeFromString<RecordItemMetrics>(entity.metricsJson)
                RecordItemWithMetrics(entity.id.value, entity.imageId, metrics)
            }
        RecordWithItems(record, items)
    }

    fun findAll(
        page: Int,
        pageSize: Int,
        testId: Int?,
        userLogin: String?,
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
}
