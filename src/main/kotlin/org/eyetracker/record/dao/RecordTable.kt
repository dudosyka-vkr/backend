package org.eyetracker.record.dao

import org.eyetracker.test.dao.TestTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object RecordTable : IntIdTable("records") {
    val testId = integer("test_id").references(TestTable.id)
    val userLogin = varchar("user_login", 255)
    val startedAt = timestamp("started_at")
    val finishedAt = timestamp("finished_at")
    val durationMs = long("duration_ms")
    val createdAt = timestamp("created_at")
}
