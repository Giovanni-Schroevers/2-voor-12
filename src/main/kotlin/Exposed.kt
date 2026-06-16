package com.example

import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

val DatabaseKey = AttributeKey<R2dbcDatabase>("R2dbcDatabase")

suspend fun Application.configureExposed() {
    val host = System.getenv("DB_HOST") ?: "localhost"
    val port = System.getenv("DB_PORT") ?: "5432"
    val name = System.getenv("DB_NAME") ?: "app"

    val database = R2dbcDatabase.connect(
        url = "r2dbc:postgresql://$host:$port/$name",
        user = System.getenv("DB_USER") ?: "app",
        password = System.getenv("DB_PASSWORD") ?: "app",
    )

    attributes.put(DatabaseKey, database)
}
