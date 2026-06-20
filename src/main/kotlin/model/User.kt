package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: UInt,
    val username: String,
    val email: String,
    val passwordHash: String,
    val avatar: String? = null,
)

@Serializable
data class UserRegistration(val username: String, val email: String, val password: String, val avatar: String? = null)
@Serializable
data class UserLogin(val username: String, val password: String)

@Serializable
data class UserResponse(val id: UInt, val username: String, val email: String, val avatar: String? = null)

@Serializable
data class UserUpdate(val username: String, val email: String, val avatar: String? = null)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class UserAuthResponse(val token: String, val user: UserResponse)