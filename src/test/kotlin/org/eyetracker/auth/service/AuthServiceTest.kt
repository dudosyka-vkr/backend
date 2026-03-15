package org.eyetracker.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.eyetracker.auth.dao.UserDao
import org.eyetracker.auth.dao.UserEntity
import org.eyetracker.auth.dao.UserTable
import org.jetbrains.exposed.dao.id.EntityID
import org.mindrot.jbcrypt.BCrypt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private lateinit var userDao: UserDao
    private lateinit var authService: AuthService
    private val jwtConfig = JwtConfig(
        secret = "test-secret",
        issuer = "http://test/",
        audience = "http://test/",
        realm = "Test",
    )

    @BeforeEach
    fun setup() {
        userDao = mockk()
        authService = AuthService(userDao, jwtConfig)
    }

    private fun mockUserEntity(id: Int, email: String, passwordHash: String, role: String = "USER"): UserEntity {
        val entity = mockk<UserEntity>()
        every { entity.id } returns EntityID(id, UserTable)
        every { entity.email } returns email
        every { entity.passwordHash } returns passwordHash
        every { entity.role } returns role
        return entity
    }

    private fun decodeToken(token: String) =
        JWT.require(Algorithm.HMAC256(jwtConfig.secret))
            .withIssuer(jwtConfig.issuer)
            .build()
            .verify(token)

    // ===== login() =====

    @Test
    fun `login success with valid credentials`() {
        val hash = BCrypt.hashpw("password123", BCrypt.gensalt())
        val user = mockUserEntity(1, "user@test.com", hash, "USER")
        every { userDao.findByLogin("user@test.com") } returns user

        val result = authService.login("user@test.com", "password123")
        assertIs<AuthResult.Success>(result)

        val decoded = decodeToken(result.token)
        assertEquals(1, decoded.getClaim("userId").asInt())
        assertEquals("user@test.com", decoded.getClaim("email").asString())
        assertEquals("USER", decoded.getClaim("role").asString())
    }

    @Test
    fun `login fails when user not found`() {
        every { userDao.findByLogin("nonexistent@test.com") } returns null
        val result = authService.login("nonexistent@test.com", "password123")
        assertIs<AuthResult.Error>(result)
        assertEquals("Invalid credentials", result.message)
    }

    @Test
    fun `login fails with wrong password`() {
        val hash = BCrypt.hashpw("correct", BCrypt.gensalt())
        val user = mockUserEntity(1, "user@test.com", hash)
        every { userDao.findByLogin("user@test.com") } returns user

        val result = authService.login("user@test.com", "wrong")
        assertIs<AuthResult.Error>(result)
        assertEquals("Invalid credentials", result.message)
    }

    // ===== register() =====

    @Test
    fun `register success creates user and returns token`() {
        every { userDao.findByLogin("new@test.com") } returns null
        val created = mockUserEntity(5, "new@test.com", "hashed", "USER")
        every { userDao.createUser(eq("new@test.com"), any(), any()) } returns created

        val result = authService.register("new@test.com", "password123")
        assertIs<AuthResult.Success>(result)
        assertNotNull(result.token)

        verify(exactly = 1) { userDao.createUser(eq("new@test.com"), any(), any()) }
    }

    @Test
    fun `register fails when user already exists`() {
        val existing = mockUserEntity(1, "existing@test.com", "hash")
        every { userDao.findByLogin("existing@test.com") } returns existing

        val result = authService.register("existing@test.com", "password123")
        assertIs<AuthResult.Error>(result)
        assertEquals("User already exists", result.message)
        verify(exactly = 0) { userDao.createUser(any(), any(), any()) }
    }

    // ===== createUser() =====

    @Test
    fun `createUser - SUPER_ADMIN creates ADMIN`() {
        every { userDao.findByLogin("newadmin@test.com") } returns null
        val created = mockUserEntity(10, "newadmin@test.com", "hashed", "ADMIN")
        every { userDao.createUser(eq("newadmin@test.com"), any(), eq("ADMIN")) } returns created

        val result = authService.createUser("newadmin@test.com", "pass", "ADMIN", "SUPER_ADMIN")
        assertIs<CreateUserResult.Success>(result)
        assertEquals("ADMIN", result.role)
    }

    @Test
    fun `createUser - SUPER_ADMIN creates USER`() {
        every { userDao.findByLogin("newuser@test.com") } returns null
        val created = mockUserEntity(11, "newuser@test.com", "hashed", "USER")
        every { userDao.createUser(eq("newuser@test.com"), any(), eq("USER")) } returns created

        val result = authService.createUser("newuser@test.com", "pass", "USER", "SUPER_ADMIN")
        assertIs<CreateUserResult.Success>(result)
        assertEquals("USER", result.role)
    }

    @Test
    fun `createUser - ADMIN creates USER`() {
        every { userDao.findByLogin("newuser@test.com") } returns null
        val created = mockUserEntity(12, "newuser@test.com", "hashed", "USER")
        every { userDao.createUser(eq("newuser@test.com"), any(), eq("USER")) } returns created

        val result = authService.createUser("newuser@test.com", "pass", "USER", "ADMIN")
        assertIs<CreateUserResult.Success>(result)
        assertEquals("USER", result.role)
    }

    @Test
    fun `createUser fails with invalid role`() {
        val result = authService.createUser("x@test.com", "pass", "MODERATOR", "SUPER_ADMIN")
        assertIs<CreateUserResult.Error>(result)
        assertEquals(400, result.status)
        assertTrue(result.message.contains("Invalid role"))
        verify(exactly = 0) { userDao.createUser(any(), any(), any()) }
    }

    @Test
    fun `createUser - ADMIN cannot create ADMIN`() {
        val result = authService.createUser("x@test.com", "pass", "ADMIN", "ADMIN")
        assertIs<CreateUserResult.Error>(result)
        assertEquals(403, result.status)
    }

    @Test
    fun `createUser - ADMIN cannot create SUPER_ADMIN`() {
        val result = authService.createUser("x@test.com", "pass", "SUPER_ADMIN", "ADMIN")
        assertIs<CreateUserResult.Error>(result)
        assertEquals(403, result.status)
    }

    @Test
    fun `createUser - SUPER_ADMIN cannot create SUPER_ADMIN`() {
        val result = authService.createUser("x@test.com", "pass", "SUPER_ADMIN", "SUPER_ADMIN")
        assertIs<CreateUserResult.Error>(result)
        assertEquals(403, result.status)
        assertEquals("Cannot create another super admin", result.message)
    }

    @Test
    fun `createUser fails on duplicate login`() {
        val existing = mockUserEntity(1, "dup@test.com", "hash")
        every { userDao.findByLogin("dup@test.com") } returns existing

        val result = authService.createUser("dup@test.com", "pass", "USER", "ADMIN")
        assertIs<CreateUserResult.Error>(result)
        assertEquals(409, result.status)
        assertEquals("User already exists", result.message)
    }

    // ===== Token claims verification =====

    @Test
    fun `generated token has correct claims and expiry`() {
        val hash = BCrypt.hashpw("pass", BCrypt.gensalt())
        val user = mockUserEntity(42, "claims@test.com", hash, "ADMIN")
        every { userDao.findByLogin("claims@test.com") } returns user

        val result = authService.login("claims@test.com", "pass")
        assertIs<AuthResult.Success>(result)

        val decoded = decodeToken(result.token)
        assertEquals(jwtConfig.audience, decoded.audience[0])
        assertEquals(jwtConfig.issuer, decoded.issuer)
        assertEquals(42, decoded.getClaim("userId").asInt())
        assertEquals("claims@test.com", decoded.getClaim("email").asString())
        assertEquals("ADMIN", decoded.getClaim("role").asString())

        val expiresIn = decoded.expiresAt.time - System.currentTimeMillis()
        assertTrue(expiresIn in 3_590_000..3_600_000, "Token should expire in ~1 hour")
    }
}
