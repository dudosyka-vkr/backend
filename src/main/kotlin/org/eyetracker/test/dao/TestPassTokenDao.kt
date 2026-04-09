package org.eyetracker.test.dao

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class TestPassTokenDao {
    fun findByTestId(testId: Int): TestPassTokenEntity? = transaction {
        TestPassTokenEntity.find { TestPassTokenTable.testId eq testId }.firstOrNull()
    }

    fun findByCode(code: String): TestPassTokenEntity? = transaction {
        TestPassTokenEntity.find { TestPassTokenTable.code eq code }.firstOrNull()
    }

    fun create(testId: Int, code: String): TestPassTokenEntity = transaction {
        TestPassTokenEntity.new {
            this.testId = testId
            this.code = code
        }
    }
}
