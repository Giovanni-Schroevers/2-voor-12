package com.example.repository

import com.example.model.*

interface UserRepository {
    suspend fun register(user: UserRegistration): UInt
    suspend fun findByUsername(username: String): User?
    suspend fun findById(id: UInt): User?
    suspend fun update(user: UserUpdate, id: UInt): UserResponse
    suspend fun updatePassword(id: UInt, passwordHash: String): Boolean
    suspend fun delete(id: UInt): Boolean
}