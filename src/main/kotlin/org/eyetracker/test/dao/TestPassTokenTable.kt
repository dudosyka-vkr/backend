package org.eyetracker.test.dao

import org.jetbrains.exposed.dao.id.IntIdTable

object TestPassTokenTable : IntIdTable("test_pass_tokens") {
    val testId = integer("test_id").references(TestTable.id)
    val code = varchar("code", 8).uniqueIndex()
}
