package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AdminLoginRequest(val password: String)

@Serializable
data class AdminTokenResponse(val token: String)

fun Application.configureAdmin() {
    val adminPassword = System.getenv("ADMIN_PASSWORD")
        ?: error("ADMIN_PASSWORD environment variable is not set")

    routing {
        route("/api/admin") {
            post("/auth") {
                val request = call.receive<AdminLoginRequest>()
                if (request.password != adminPassword) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid password")
                    return@post
                }
                call.respond(AdminTokenResponse(JwtConfig.makeToken(subject = "admin")))
            }
        }
    }
}
