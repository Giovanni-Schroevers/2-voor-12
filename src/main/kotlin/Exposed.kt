package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

suspend fun Application.configureExposed(): R2dbcDatabase {
    val host = System.getenv("DB_HOST") ?: "localhost"
    val port = System.getenv("DB_PORT") ?: "5432"
    val name = System.getenv("DB_NAME") ?: "app"

    return R2dbcDatabase.connect(
        url = "r2dbc:postgresql://$host:$port/$name",
        user = System.getenv("DB_USER") ?: "app",
        password = System.getenv("DB_PASSWORD") ?: "app",
    )
}
