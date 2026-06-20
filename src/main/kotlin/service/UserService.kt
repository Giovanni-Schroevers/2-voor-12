package com.example.service

import com.example.JwtConfig
import com.example.Passwords
import com.example.model.ChangePasswordRequest
import com.example.model.User
import com.example.model.UserAuthResponse
import com.example.model.UserLogin
import com.example.model.UserRegistration
import com.example.model.UserResponse
import com.example.model.UserUpdate
import com.example.repository.UserRepository

class UserService(private val repository: UserRepository) {
    /** Hashes the password before handing the registration to the repository. */
    suspend fun register(user: UserRegistration): UInt =
        repository.register(user.copy(password = Passwords.hash(user.password)))

    /** Verifies credentials against the stored hash and, on success, issues a token. */
    suspend fun login(credentials: UserLogin): UserAuthResponse? {
        val user = repository.findByUsername(credentials.username) ?: return null
        if (!Passwords.verify(credentials.password, user.passwordHash)) return null
        val token = JwtConfig.makeToken(subject = user.id.toString(), role = JwtConfig.USER_ROLE)
        return UserAuthResponse(token, UserResponse(user.id, user.username, user.email, user.avatar))
    }

    suspend fun update(user: UserUpdate, id: UInt): UserResponse = repository.update(user, id)
    suspend fun delete(id: UInt): Boolean = repository.delete(id)

    /**
     * Verifies the current password, then stores a hash of the new one.
     * Returns false if the user is gone or the current password does not match.
     */
    suspend fun changePassword(id: UInt, request: ChangePasswordRequest): Boolean {
        val user = repository.findById(id) ?: return false
        if (!Passwords.verify(request.currentPassword, user.passwordHash)) return false
        return repository.updatePassword(id, Passwords.hash(request.newPassword))
    }
}
