package com.example.routing

import com.example.AvatarStorage
import com.example.InvalidAvatarException
import com.example.JwtConfig
import com.example.model.ChangePasswordRequest
import com.example.model.UserLogin
import com.example.model.UserRegistration
import com.example.model.UserUpdate
import com.example.service.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put

fun Route.userRoutes(service: UserService, avatars: AvatarStorage) {
    post("/register") {
        val data = call.receive<UserRegistration>()

        call.respond(HttpStatusCode.Created, service.register(data))
    }

    post("/login") {
        val data = call.receive<UserLogin>()

        val auth = service.login(data)
        if (auth == null) call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        else call.respond(auth)
    }

    authenticate(JwtConfig.USER_PROVIDER) {
        put("/user/update") {
            val id = call.principal<JWTPrincipal>()?.subject?.toUIntOrNull()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val data = call.receive<UserUpdate>()

            val updated = service.update(data, id)
                ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }

        // The single multipart endpoint: upload/replace the authenticated user's avatar image.
        post("/user/avatar") {
            val id = call.principal<JWTPrincipal>()?.subject?.toUIntOrNull()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val form = try {
                call.receiveAvatarForm(avatars)
            } catch (e: InvalidAvatarException) {
                return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid avatar")
            }

            val avatarPath = form.avatarPath
                ?: return@post call.respond(HttpStatusCode.BadRequest, "An 'avatar' file part is required")

            val updated = service.updateAvatar(id, avatarPath)
                ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }

        put("/user/password") {
            val id = call.principal<JWTPrincipal>()?.subject?.toUIntOrNull()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val data = call.receive<ChangePasswordRequest>()

            if (service.changePassword(id, data)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.BadRequest, "Current password is incorrect")
        }

        delete("/user/delete") {
            val id = call.principal<JWTPrincipal>()?.subject?.toUIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.Unauthorized)

            if (service.delete(id)) call.respond(HttpStatusCode.NoContent)
            else call.respond(HttpStatusCode.NotFound)
        }
    }
}
