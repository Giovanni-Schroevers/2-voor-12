package com.example.service

import com.example.AvatarStorage
import com.example.JwtConfig
import com.example.Passwords
import com.example.model.ChangePasswordRequest
import com.example.model.UserAuthResponse
import com.example.model.UserLogin
import com.example.model.UserRegistration
import com.example.model.UserResponse
import com.example.model.UserUpdate
import com.example.repository.UserRepository

class UserService(private val repository: UserRepository, private val avatars: AvatarStorage) {
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

    /**
     * Updates username/email only; the avatar is left untouched (it is managed by [updateAvatar]).
     * Returns null if the user no longer exists.
     */
    suspend fun update(user: UserUpdate, id: UInt): UserResponse? {
        val existing = repository.findById(id) ?: return null
        repository.update(user, id)
        return UserResponse(id, user.username, user.email, existing.avatar)
    }

    /**
     * Replaces the user's avatar with the already-stored [avatarPath], deleting the previous
     * image. If the user no longer exists, the just-uploaded file is cleaned up and null returned.
     */
    suspend fun updateAvatar(id: UInt, avatarPath: String): UserResponse? {
        val existing = repository.findById(id) ?: run {
            avatars.delete(avatarPath)
            return null
        }
        avatars.delete(existing.avatar)
        repository.updateAvatar(id, avatarPath)
        return UserResponse(id, existing.username, existing.email, avatarPath)
    }

    suspend fun delete(id: UInt): Boolean {
        val avatar = repository.findById(id)?.avatar
        return repository.delete(id).also { removed -> if (removed) avatars.delete(avatar) }
    }

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
