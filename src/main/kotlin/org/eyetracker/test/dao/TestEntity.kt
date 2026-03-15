package org.eyetracker.test.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TestEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TestEntity>(TestTable)

    var name by TestTable.name
    var coverFilename by TestTable.coverFilename
    var userId by TestTable.userId
    var createdAt by TestTable.createdAt
}
