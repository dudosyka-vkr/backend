package org.eyetracker.test.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class TestImageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TestImageEntity>(TestImageTable)

    var testId by TestImageTable.testId
    var filename by TestImageTable.filename
    var sortOrder by TestImageTable.sortOrder
    var fixationTrackingArea by TestImageTable.fixationTrackingArea
}
