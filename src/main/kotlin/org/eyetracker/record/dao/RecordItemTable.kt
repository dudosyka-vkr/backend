package org.eyetracker.record.dao

import org.eyetracker.test.dao.TestImageTable
import org.jetbrains.exposed.dao.id.IntIdTable

object RecordItemTable : IntIdTable("record_items") {
    val recordId = integer("record_id").references(RecordTable.id)
    val imageId = integer("image_id").references(TestImageTable.id)
    val metricsJson = text("metrics_json")
}
