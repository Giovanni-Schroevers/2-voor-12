package com.example

import com.example.model.Question
import com.example.repository.ExposedQuestionRepository
import com.example.service.QuestionAssistantService
import com.example.service.QuestionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class DraftRequest(val description: String)

suspend fun Application.configureQuestions() {
    val database = attributes[DatabaseKey]
    val repository = ExposedQuestionRepository(database).also { it.createSchema() }
    val service = QuestionService(repository)

    val apiKey = System.getenv("GOOGLE_API_KEY")
        ?: error("GOOGLE_API_KEY environment variable is not set")
    val assistant = QuestionAssistantService(apiKey)

    routing {
        authenticate(JwtConfig.ADMIN_PROVIDER) {
            route("/api/admin/questions") {
                get {
                    call.respond(service.list())
                }

                get("/summary") {
                    call.respond(service.summary())
                }

                post("/assistant") {
                    val request = try {
                        call.receive<DraftRequest>()
                    } catch (e: Exception) {
                        return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request body")
                    }
                    call.respond(assistant.draftQuestion(request.description))
                }

                post {
                    val question = receiveQuestion() ?: return@post
                    call.respond(HttpStatusCode.Created, service.create(question))
                }

                get("/{id}") {
                    val id = pathId() ?: return@get
                    val question = service.get(id)
                    if (question == null) call.respond(HttpStatusCode.NotFound)
                    else call.respond(question)
                }

                put("/{id}") {
                    val id = pathId() ?: return@put
                    val question = receiveQuestion() ?: return@put
                    if (service.update(id, question)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound)
                }

                delete("/{id}") {
                    val id = pathId() ?: return@delete
                    if (service.delete(id)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

/** Parses the {id} path parameter, responding 400 if it is missing or invalid. */
private suspend fun RoutingContext.pathId(): UInt? {
    val id = call.parameters["id"]?.toUIntOrNull()
    if (id == null) call.respond(HttpStatusCode.BadRequest, "Invalid question id")
    return id
}

/** Reads a [Question] body, responding 400 if it is malformed or fails validation. */
private suspend fun RoutingContext.receiveQuestion(): Question? =
    try {
        call.receive<Question>()
    } catch (e: Exception) {
        call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid question body")
        null
    }
