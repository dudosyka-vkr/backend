package org.eyetracker.test.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TestPassTokenEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TestPassTokenEntity>(TestPassTokenTable)

    var code by TestPassTokenTable.code
    var testId by TestPassTokenTable.testId
}
