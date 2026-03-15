package org.eyetracker.test.dao

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

data class TestWithImages(
    val test: TestEntity,
    val imageFilenames: List<String>,
)

class TestDao {
    fun create(
        name: String,
        coverFilename: String,
        imageFilenames: List<String>,
        userId: Int,
    ): TestWithImages = transaction {
        val test = TestEntity.new {
            this.name = name
            this.coverFilename = coverFilename
            this.userId = userId
            this.createdAt = Clock.System.now()
        }
        imageFilenames.forEachIndexed { index, filename ->
            TestImageEntity.new {
                this.testId = test.id.value
                this.filename = filename
                this.sortOrder = index
            }
        }
        TestWithImages(test, imageFilenames)
    }

    fun findAll(): List<TestWithImages> = transaction {
        val tests = TestEntity.all().toList()
        tests.map { test ->
            val images = TestImageEntity.find { TestImageTable.testId eq test.id.value }
                .orderBy(TestImageTable.sortOrder to SortOrder.ASC)
                .map { it.filename }
            TestWithImages(test, images)
        }
    }

    fun findById(testId: Int): TestWithImages? = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction null
        val images = TestImageEntity.find { TestImageTable.testId eq testId }
            .orderBy(TestImageTable.sortOrder to SortOrder.ASC)
            .map { it.filename }
        TestWithImages(test, images)
    }

    fun update(
        testId: Int,
        name: String,
        coverFilename: String,
        imageFilenames: List<String>,
    ): TestWithImages? = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction null
        test.name = name
        test.coverFilename = coverFilename
        TestImageTable.deleteWhere { TestImageTable.testId eq testId }
        imageFilenames.forEachIndexed { index, filename ->
            TestImageEntity.new {
                this.testId = testId
                this.filename = filename
                this.sortOrder = index
            }
        }
        TestWithImages(test, imageFilenames)
    }

    fun deleteById(testId: Int): Boolean = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction false
        TestImageTable.deleteWhere { TestImageTable.testId eq testId }
        test.delete()
        true
    }
}
