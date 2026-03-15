package org.eyetracker.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.eyetracker.auth.dao.UserDao
import org.mindrot.jbcrypt.BCrypt
import java.util.*

object Role {
    const val USER = "USER"
    const val ADMIN = "ADMIN"
    const val SUPER_ADMIN = "SUPER_ADMIN"

    val ALL = setOf(USER, ADMIN, SUPER_ADMIN)
}

sealed class AuthResult {
    data class Success(val token: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class CreateUserResult {
    data class Success(val id: Int, val login: String, val role: String) : CreateUserResult()
    data class Error(val message: String, val status: Int) : CreateUserResult()
}

class AuthService(
    private val userDao: UserDao,
    private val jwtConfig: JwtConfig,
) {
    fun login(login: String, password: String): AuthResult {
        val user = userDao.findByLogin(login)
            ?: return AuthResult.Error("Invalid credentials")

        if (!BCrypt.checkpw(password, user.passwordHash)) {
            return AuthResult.Error("Invalid credentials")
        }

        val token = generateToken(user.id.value, user.email, user.role)
        return AuthResult.Success(token)
    }

    fun register(login: String, password: String): AuthResult {
        if (userDao.findByLogin(login) != null) {
            return AuthResult.Error("User already exists")
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userDao.createUser(login, hashedPassword)

        val token = generateToken(user.id.value, user.email, user.role)
        return AuthResult.Success(token)
    }

    fun createUser(login: String, password: String, targetRole: String, callerRole: String): CreateUserResult {
        if (targetRole !in Role.ALL) {
            return CreateUserResult.Error("Invalid role: $targetRole", 400)
        }

        if (callerRole == Role.ADMIN && targetRole != Role.USER) {
            return CreateUserResult.Error("Admin can only create users with USER role", 403)
        }

        if (callerRole == Role.SUPER_ADMIN && targetRole == Role.SUPER_ADMIN) {
            return CreateUserResult.Error("Cannot create another super admin", 403)
        }

        if (userDao.findByLogin(login) != null) {
            return CreateUserResult.Error("User already exists", 409)
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userDao.createUser(login, hashedPassword, targetRole)
        return CreateUserResult.Success(user.id.value, user.email, user.role)
    }

    private fun generateToken(userId: Int, email: String, role: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}
