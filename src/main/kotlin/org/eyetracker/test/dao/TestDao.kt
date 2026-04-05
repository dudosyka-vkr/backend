package org.eyetracker.test.dao

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction

data class TestWithImages(
    val test: TestEntity,
    val imageFilenames: List<String>,
    val imageIds: List<Int> = emptyList(),
    val rois: List<String?> = emptyList(),
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
        val images = imageFilenames.mapIndexed { index, filename ->
            TestImageEntity.new {
                this.testId = test.id.value
                this.filename = filename
                this.sortOrder = index
            }
        }
        TestWithImages(test, imageFilenames, images.map { it.id.value }, images.map { it.roi })
    }

    fun findAll(): List<TestWithImages> = transaction {
        val tests = TestEntity.all().toList()
        tests.map { test ->
            val imageEntities = TestImageEntity.find { TestImageTable.testId eq test.id.value }
                .orderBy(TestImageTable.sortOrder to SortOrder.ASC)
                .toList()
            TestWithImages(
                test,
                imageEntities.map { it.filename },
                imageEntities.map { it.id.value },
                imageEntities.map { it.roi },
            )
        }
    }

    fun findById(testId: Int): TestWithImages? = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction null
        val imageEntities = TestImageEntity.find { TestImageTable.testId eq testId }
            .orderBy(TestImageTable.sortOrder to SortOrder.ASC)
            .toList()
        TestWithImages(
            test,
            imageEntities.map { it.filename },
            imageEntities.map { it.id.value },
            imageEntities.map { it.roi },
        )
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
        val images = imageFilenames.mapIndexed { index, filename ->
            TestImageEntity.new {
                this.testId = testId
                this.filename = filename
                this.sortOrder = index
            }
        }
        TestWithImages(test, imageFilenames, images.map { it.id.value }, images.map { it.roi })
    }

    fun findImageIdsByTestId(testId: Int): List<Int> = transaction {
        TestImageEntity.find { TestImageTable.testId eq testId }
            .orderBy(TestImageTable.sortOrder to SortOrder.ASC)
            .map { it.id.value }
    }

    fun updateImageRoi(imageId: Int, roi: String): Boolean = transaction {
        val image = TestImageEntity.findById(imageId) ?: return@transaction false
        image.roi = roi
        true
    }

    fun deleteById(testId: Int): Boolean = transaction {
        val test = TestEntity.findById(testId) ?: return@transaction false
        TestImageTable.deleteWhere { TestImageTable.testId eq testId }
        test.delete()
        true
    }
}
