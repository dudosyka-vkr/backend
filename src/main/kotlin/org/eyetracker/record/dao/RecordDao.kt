package org.eyetracker.record.dao

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class RecordDao {
    fun create(
        testId: Int,
        userLogin: String,
        startedAt: Instant,
        finishedAt: Instant,
        durationMs: Long,
        metricsJson: String,
    ): RecordEntity = transaction {
        RecordEntity.new {
            this.testId = testId
            this.userLogin = userLogin
            this.startedAt = startedAt
            this.finishedAt = finishedAt
            this.durationMs = durationMs
            this.metricsJson = metricsJson
            this.createdAt = Clock.System.now()
        }
    }

    fun findById(id: Int): RecordEntity? = transaction {
        RecordEntity.findById(id)
    }

    fun findAllForTest(testId: Int): List<RecordEntity> = transaction {
        RecordEntity.find { RecordTable.testId eq testId }.toList()
    }

    fun updateMetrics(id: Int, metricsJson: String): Unit = transaction {
        RecordTable.update({ RecordTable.id eq id }) {
            it[RecordTable.metricsJson] = metricsJson
        }
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
        if (testId != null) query.andWhere { RecordTable.testId eq testId }
        if (!userLogin.isNullOrBlank()) query.andWhere { RecordTable.userLogin eq userLogin }
        if (!userLoginContains.isNullOrBlank()) {
            query.andWhere { RecordTable.userLogin.lowerCase() like "%${userLoginContains.lowercase()}%" }
        }
        if (from != null) query.andWhere { RecordTable.startedAt greaterEq from }
        if (to != null) query.andWhere { RecordTable.startedAt lessEq to }
        val total = query.count()
        query.orderBy(RecordTable.id, SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
        Pair(RecordEntity.wrapRows(query).toList(), total)
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

    fun suggestUsers(
        page: Int,
        pageSize: Int,
        testId: Int?,
        from: Instant?,
        to: Instant?,
    ): Pair<List<String>, Long> = transaction {
        val query = RecordTable.select(RecordTable.userLogin).withDistinct()
        if (testId != null) query.andWhere { RecordTable.testId eq testId }
        if (from != null) query.andWhere { RecordTable.startedAt greaterEq from }
        if (to != null) query.andWhere { RecordTable.startedAt lessEq to }
        val total = query.count()
        query.orderBy(RecordTable.userLogin, SortOrder.ASC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
        Pair(query.map { it[RecordTable.userLogin] }, total)
    }
}
