package org.eyetracker.record.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class RecordEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RecordEntity>(RecordTable)

    var testId by RecordTable.testId
    var userLogin by RecordTable.userLogin
    var startedAt by RecordTable.startedAt
    var finishedAt by RecordTable.finishedAt
    var durationMs by RecordTable.durationMs
    var createdAt by RecordTable.createdAt
}
