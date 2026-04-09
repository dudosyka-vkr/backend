package org.eyetracker.test.dao

import org.jetbrains.exposed.dao.id.IntIdTable

object TestPassTokenTable : IntIdTable("test_pass_tokens") {
    val code = varchar("code", 8).uniqueIndex()
    val testId = integer("test_id").references(TestTable.id)
}
