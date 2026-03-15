package org.eyetracker.auth.dao

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserEntity>(UserTable)

    var email by UserTable.email
    var passwordHash by UserTable.passwordHash
    var role by UserTable.role
    var createdAt by UserTable.createdAt
}
