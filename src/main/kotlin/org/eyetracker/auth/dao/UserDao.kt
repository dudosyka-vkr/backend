package org.eyetracker.auth.dao

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.transactions.transaction

class UserDao {
    fun findByLogin(login: String): UserEntity? = transaction {
        UserEntity.find { UserTable.email eq login }.firstOrNull()
    }

    fun createUser(login: String, passwordHash: String): UserEntity = transaction {
        UserEntity.new {
            email = login
            this.passwordHash = passwordHash
            createdAt = Clock.System.now()
        }
    }
}
