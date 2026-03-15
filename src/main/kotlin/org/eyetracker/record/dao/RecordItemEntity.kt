package org.eyetracker.record.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class RecordItemEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RecordItemEntity>(RecordItemTable)

    var recordId by RecordItemTable.recordId
    var imageId by RecordItemTable.imageId
    var metricsJson by RecordItemTable.metricsJson
}
