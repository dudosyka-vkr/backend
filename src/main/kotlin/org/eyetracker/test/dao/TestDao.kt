package org.eyetracker.test.dao

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.transactions.transaction

class TestDao {
    fun create(name: String, image: String, userId: Int): TestEntity = transaction {
        TestEntity.new {
            this.name = name
            this.image = image
            this.userId = userId
            this.createdAt = Clock.System.now()
        }
    }

    fun updateName(testId: Int, name: String): Boolean = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction false
        test.name = name
        true
    }

    fun updateAoi(testId: Int, aoi: String): Boolean = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction false
        test.aoi = aoi
        true
    }

    fun deleteById(testId: Int): Boolean = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction false
        test.delete()
        true
    }

    fun findById(testId: Int): TestEntity? = transaction {
        TestEntity.findById(testId)
    }

    fun findAll(nameFilter: String? = null): List<TestEntity> = transaction {
        if (nameFilter.isNullOrBlank()) {
            TestEntity.all().toList()
        } else {
            TestEntity.find {
                TestTable.name.lowerCase() like "%${nameFilter.lowercase()}%"
            }.toList()
        }
    }
}
