package com.example.routing

import com.example.JwtConfig
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AdminLoginRequest(val password: String)

@Serializable
data class AdminTokenResponse(val token: String)

/** Admin authentication endpoint. The expected password is supplied by the caller. */
fun Route.adminRoutes(adminPassword: String) {
    route("/admin") {
        post("/auth") {
            val request = call.receive<AdminLoginRequest>()
            if (request.password != adminPassword) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid password")
                return@post
            }
            call.respond(AdminTokenResponse(JwtConfig.makeToken(subject = "admin", role = JwtConfig.ADMIN_ROLE)))
        }
    }
}
