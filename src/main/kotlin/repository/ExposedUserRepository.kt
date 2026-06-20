package com.example.repository

import com.example.model.User
import com.example.model.UserRegistration
import com.example.model.UserResponse
import com.example.model.UserUpdate
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UIntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

class ExposedUserRepository(private val database: R2dbcDatabase): UserRepository {
    object Users : UIntIdTable("users") {
        val username = varchar("username", 255).uniqueIndex()
        val email = varchar("email", 255).uniqueIndex()
        val password = varchar("password", 255)
        val avatar = varchar("avatar", 255).nullable()
    }

    suspend fun createSchema() {
        suspendTransaction(database) {
            SchemaUtils.create(Users)
        }
    }

    override suspend fun register(user: UserRegistration): UInt = suspendTransaction(database) {
        val id = Users.insertAndGetId {
            it[username] = user.username
            it[email] = user.email
            it[password] = user.password
            it[avatar] = user.avatar
        }

        id.value
    }

    override suspend fun findByUsername(username: String): User? = suspendTransaction(database) {
        Users.select(listOf(Users.id, Users.username, Users.email, Users.password, Users.avatar))
            .where { Users.username eq username }
            .firstOrNull()
            ?.toUser()
    }

    override suspend fun findById(id: UInt): User? = suspendTransaction(database) {
        Users.select(listOf(Users.id, Users.username, Users.email, Users.password, Users.avatar))
            .where { Users.id eq id }
            .firstOrNull()
            ?.toUser()
    }

    override suspend fun update(user: UserUpdate, id: UInt): UserResponse = suspendTransaction(database) {
        Users.update({ Users.id eq id }) {
            it[username] = user.username
            it[email] = user.email
            it[avatar] = user.avatar
        }

        UserResponse(
            id = id,
            username = user.username,
            email = user.email,
            avatar = user.avatar
        )
    }

    override suspend fun updatePassword(id: UInt, passwordHash: String): Boolean = suspendTransaction(database) {
        Users.update({ Users.id eq id }) {
            it[password] = passwordHash
        } > 0
    }

    override suspend fun delete(id: UInt): Boolean = suspendTransaction(database) {
        Users.deleteWhere { Users.id eq id } > 0
    }

    private fun ResultRow.toUser() = User(
        id = this[Users.id].value,
        username = this[Users.username],
        email = this[Users.email],
        passwordHash = this[Users.password],
        avatar = this[Users.avatar],
    )
}