package org.eyetracker.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class RegisterRequest(val login: String, val password: String)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class CreateUserRequest(val login: String, val password: String, val role: String)

@Serializable
data class UserResponse(val id: Int, val login: String, val role: String)

@Serializable
data class RoleResponse(val role: String)

@Serializable
data class ErrorResponse(val error: String)
