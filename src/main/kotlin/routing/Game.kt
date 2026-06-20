package com.example.routing

import com.example.model.PuzzlePreference
import com.example.service.SoloGameService
import com.example.service.UnsatisfiableRoundException
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class SoloRoundRequest(val puzzle: PuzzlePreference)

/** Public solo-game endpoints. The client owns the timer and scoring. */
fun Route.gameRoutes(soloGame: SoloGameService) {
    route("/game") {
        post("/solo") {
            val request = try {
                call.receive<SoloRoundRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request body")
            }

            try {
                call.respond(soloGame.generateRound(request.puzzle))
            } catch (e: UnsatisfiableRoundException) {
                call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Could not generate a round")
            }
        }
    }
}
