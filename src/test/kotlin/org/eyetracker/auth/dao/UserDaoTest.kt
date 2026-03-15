package org.eyetracker.auth.dao

import org.eyetracker.base.DatabaseTestBase
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserDaoTest : DatabaseTestBase() {

    private val userDao = UserDao()

    @Test
    fun `findByLogin returns user when exists`() {
        userDao.createUser("user@test.com", "hashed_pw")
        val found = userDao.findByLogin("user@test.com")
        assertNotNull(found)
        assertEquals("user@test.com", found.email)
        assertEquals("USER", found.role)
        assertNotNull(found.createdAt)
    }

    @Test
    fun `findByLogin returns null when not exists`() {
        val found = userDao.findByLogin("nonexistent@test.com")
        assertNull(found)
    }

    @Test
    fun `findByLogin is case sensitive`() {
        userDao.createUser("User@Test.com", "hashed_pw")
        assertNull(userDao.findByLogin("user@test.com"))
        assertNotNull(userDao.findByLogin("User@Test.com"))
    }

    @Test
    fun `createUser with default role`() {
        val user = userDao.createUser("new@test.com", "hashed_pw")
        assertEquals("new@test.com", user.email)
        assertEquals("hashed_pw", user.passwordHash)
        assertEquals("USER", user.role)
        assert(user.id.value > 0)
        assertNotNull(user.createdAt)

        val found = userDao.findByLogin("new@test.com")
        assertNotNull(found)
        assertEquals(user.id, found.id)
    }

    @Test
    fun `createUser with explicit ADMIN role`() {
        val user = userDao.createUser("admin@test.com", "hashed_pw", "ADMIN")
        assertEquals("ADMIN", user.role)
    }

    @Test
    fun `createUser with explicit SUPER_ADMIN role`() {
        val user = userDao.createUser("sa@test.com", "hashed_pw", "SUPER_ADMIN")
        assertEquals("SUPER_ADMIN", user.role)
    }

    @Test
    fun `createUser throws on duplicate email`() {
        userDao.createUser("dup@test.com", "hashed_pw")
        assertThrows<Exception> {
            userDao.createUser("dup@test.com", "hashed_pw2")
        }
    }
}
