package org.eyetracker.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.eyetracker.auth.dao.UserDao
import org.mindrot.jbcrypt.BCrypt
import java.util.*

sealed class AuthResult {
    data class Success(val token: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
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

        val token = generateToken(user.id.value, user.email)
        return AuthResult.Success(token)
    }

    fun register(login: String, password: String): AuthResult {
        if (userDao.findByLogin(login) != null) {
            return AuthResult.Error("User already exists")
        }

        val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userDao.createUser(login, hashedPassword)

        val token = generateToken(user.id.value, user.email)
        return AuthResult.Success(token)
    }

    private fun generateToken(userId: Int, email: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}
