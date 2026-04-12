package org.eyetracker.test.dao

import org.eyetracker.auth.dao.UserTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object TestTable : IntIdTable("tests") {
    val name = varchar("name", 255)
    val image = varchar("image", 255)
    val aoi = text("aoi").nullable()
    val userId = integer("user_id").references(UserTable.id)
    val createdAt = timestamp("created_at")
}
