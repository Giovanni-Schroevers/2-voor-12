package com.example

import com.example.repository.ExposedQuestionRepository
import com.example.repository.ExposedUserRepository
import com.example.routing.adminRoutes
import com.example.routing.questionRoutes
import com.example.routing.userRoutes
import com.example.service.QuestionAssistantService
import com.example.service.QuestionService
import com.example.service.UserService
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.io.File

/**
 * Composition root: reads configuration, builds the dependency graph (repositories
 * and services), and registers every route. Individual `*Routes` functions only
 * declare endpoints and receive their dependencies from here, so they stay testable
 * in isolation.
 */
suspend fun Application.configureApp(database: R2dbcDatabase) {
    // --- configuration ---
    val adminPassword = System.getenv("ADMIN_PASSWORD")
        ?: error("ADMIN_PASSWORD environment variable is not set")
    val googleApiKey = System.getenv("GOOGLE_API_KEY")
        ?: error("GOOGLE_API_KEY environment variable is not set")
    val uploadDir = System.getenv("UPLOAD_DIR") ?: "uploads"

    // --- dependency construction ---
    val questionRepository = ExposedQuestionRepository(database).also { it.createSchema() }
    val questionService = QuestionService(questionRepository)
    val questionAssistant = QuestionAssistantService(googleApiKey)

    val avatarDir = File(uploadDir, AvatarStorage.RELATIVE_PREFIX)
    val avatarStorage = AvatarStorage(avatarDir)

    val userRepository = ExposedUserRepository(database).also { it.createSchema() }
    val userService = UserService(userRepository, avatarStorage)

    // --- routes ---
    routing {
        get("/") {
            call.respondText("2 voor 12 API is running")
        }
        // Serve stored avatars at /avatars/<file>, matching the path persisted in the DB.
        staticFiles("/${AvatarStorage.RELATIVE_PREFIX}", avatarDir)
        route("/api") {
            adminRoutes(adminPassword)
            questionRoutes(questionService, questionAssistant)
            userRoutes(userService, avatarStorage)
        }
    }
}
