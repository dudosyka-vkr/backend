package org.eyetracker.test.dao

import org.jetbrains.exposed.dao.id.IntIdTable

object TestImageTable : IntIdTable("test_images") {
    val testId = integer("test_id").references(TestTable.id)
    val filename = varchar("filename", 255)
    val sortOrder = integer("sort_order")
}
