package com.example.routing

import com.example.game.GameSession
import com.example.game.JoinResult
import com.example.game.Lobby
import com.example.game.LobbyManager
import com.example.game.Player
import com.example.model.ClientMessage
import com.example.model.PlayerProfile
import com.example.model.QuizMode
import com.example.model.ServerMessage
import com.example.service.RoundGenerator
import com.example.service.UnsatisfiableRoundException
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Websocket endpoints for the online game.
 *
 * Both hosting and joining use the same `/ws/lobby` endpoint. The client passes
 * its own identity and (optionally) a code as query parameters:
 *   - `?username=Bob`                    -> create a new lobby and host it
 *   - `?username=Alice&code=K7Q2`        -> join the existing lobby K7Q2
 *   - `&avatar=<path>`                   -> optional, included with either of the above
 *
 * Game flow once both players are connected:
 *   1. host sends `start_game` -> both receive `game_started` with their round
 *   2. both send `finished_answering` (or [answeringTimeout] elapses)
 *      -> both receive `proceed_to_word` at the same moment
 *   3. both send `submit_score` -> both receive `game_finished`, then the
 *      connections are closed
 */
fun Route.gameSocketRoutes(
    lobbyManager: LobbyManager,
    rounds: RoundGenerator,
    answeringTimeout: Duration = 15.minutes,
) {
    webSocket("/ws/lobby") {
        val username = call.request.queryParameters["username"]
        if (username.isNullOrBlank()) {
            closeWithError("A username is required.")
            return@webSocket
        }
        val profile = PlayerProfile(
            username = username,
            avatar = call.request.queryParameters["avatar"],
        )

        val code = call.request.queryParameters["code"]
        if (code == null) {
            hostLobby(lobbyManager, profile, rounds, answeringTimeout)
        } else {
            joinLobby(lobbyManager, code, profile, rounds, answeringTimeout)
        }
    }
}

/** Creates a lobby, hands the host its join code, and keeps them connected. */
private suspend fun DefaultWebSocketServerSession.hostLobby(
    lobbyManager: LobbyManager,
    profile: PlayerProfile,
    rounds: RoundGenerator,
    answeringTimeout: Duration,
) {
    val lobby = lobbyManager.create(host = Player(session = this, profile = profile))
    sendSerialized<ServerMessage>(ServerMessage.LobbyCreated(lobby.code))

    try {
        handleMessages(lobby, isHost = true, rounds, answeringTimeout)
    } finally {
        lobbyManager.remove(lobby.code)
    }
}

/** Seats the caller as a guest in [code]'s lobby, or closes with an error. */
private suspend fun DefaultWebSocketServerSession.joinLobby(
    lobbyManager: LobbyManager,
    code: String,
    profile: PlayerProfile,
    rounds: RoundGenerator,
    answeringTimeout: Duration,
) {
    val guest = Player(session = this, profile = profile)
    when (val result = lobbyManager.join(code, guest)) {
        JoinResult.NotFound -> closeWithError("No lobby found for code \"$code\".")
        JoinResult.Full -> closeWithError("Lobby \"$code\" already has two players.")
        is JoinResult.Success -> {
            val lobby = result.lobby
            // Tell each side who their opponent is.
            sendSerialized<ServerMessage>(ServerMessage.JoinedLobby(lobby.code, opponent = lobby.host.profile))
            lobby.host.session.sendSerialized<ServerMessage>(ServerMessage.PlayerJoined(opponent = guest.profile))

            try {
                handleMessages(lobby, isHost = false, rounds, answeringTimeout)
            } finally {
                lobby.removeGuest(this)
            }
        }
    }
}

/** Reads client messages for this connection until it closes. */
private suspend fun DefaultWebSocketServerSession.handleMessages(
    lobby: Lobby,
    isHost: Boolean,
    rounds: RoundGenerator,
    answeringTimeout: Duration,
) {
    while (true) {
        val message = try {
            receiveDeserialized<ClientMessage>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClosedReceiveChannelException) {
            break // the client disconnected
        } catch (e: Exception) {
            sendSerialized<ServerMessage>(ServerMessage.LobbyError("Could not read your message."))
            continue
        }

        when (message) {
            is ClientMessage.StartGame -> startGame(lobby, isHost, message, rounds, answeringTimeout)
            ClientMessage.FinishedAnswering -> finishAnswering(lobby, isHost)
            is ClientMessage.SubmitScore -> submitScore(lobby, isHost, message.score)
        }
    }
}

/** Handles a host's request to start the match, generating and dealing the rounds. */
private suspend fun DefaultWebSocketServerSession.startGame(
    lobby: Lobby,
    isHost: Boolean,
    request: ClientMessage.StartGame,
    rounds: RoundGenerator,
    answeringTimeout: Duration,
) {
    if (!isHost) {
        return sendSerialized<ServerMessage>(ServerMessage.LobbyError("Only the host can start the game."))
    }
    if (lobby.guest == null) {
        return sendSerialized<ServerMessage>(ServerMessage.LobbyError("Cannot start until another player has joined."))
    }

    // Generate before claiming the start, so a failure leaves the host free to retry.
    val (hostRound, guestRound) = try {
        when (request.quizMode) {
            QuizMode.SAME -> rounds.generateRound(request.puzzle).let { it to it }
            QuizMode.DIFFERENT -> rounds.generateRound(request.puzzle) to rounds.generateRound(request.puzzle)
        }
    } catch (e: UnsatisfiableRoundException) {
        return sendSerialized<ServerMessage>(ServerMessage.LobbyError("Could not generate a quiz right now. Please try again."))
    }

    val game = lobby.startGame()
        ?: return sendSerialized<ServerMessage>(ServerMessage.LobbyError("The game has already started."))

    game.host.session.sendSerialized<ServerMessage>(ServerMessage.GameStarted(hostRound))
    game.guest.session.sendSerialized<ServerMessage>(ServerMessage.GameStarted(guestRound))

    // The answering phase is capped: if both players don't finish in time, the
    // server advances everyone to the word phase. Run on the application scope so
    // it is independent of either connection's lifecycle.
    game.timerJob = call.application.launch {
        try {
            delay(answeringTimeout)
            if (game.startWordPhaseOnTimeout()) {
                broadcast(game, ServerMessage.ProceedToWord)
            }
        } catch (e: CancellationException) {
            // Cancelled because both players finished early; nothing to do.
        } catch (e: Exception) {
            // A player may have disconnected mid-broadcast; ignore for now.
        }
    }
}

/** Marks a player as done answering and, once both are, advances to the word phase. */
private suspend fun DefaultWebSocketServerSession.finishAnswering(lobby: Lobby, isHost: Boolean) {
    val game = lobby.game
        ?: return sendSerialized<ServerMessage>(ServerMessage.LobbyError("The game has not started yet."))

    if (game.finishAnswering(isHost)) {
        game.timerJob?.cancel()
        broadcast(game, ServerMessage.ProceedToWord)
    }
}

/** Records a player's score and, once both are in, reports the result and closes. */
private suspend fun DefaultWebSocketServerSession.submitScore(lobby: Lobby, isHost: Boolean, score: Int) {
    val game = lobby.game
        ?: return sendSerialized<ServerMessage>(ServerMessage.LobbyError("The game has not started yet."))

    val result = game.submitScore(isHost, score) ?: return
    broadcast(game, ServerMessage.GameFinished(result))
    game.host.session.close(CloseReason(CloseReason.Codes.NORMAL, "Game finished"))
    game.guest.session.close(CloseReason(CloseReason.Codes.NORMAL, "Game finished"))
}

/** Sends [message] to both players of [game]. */
private suspend fun broadcast(game: GameSession, message: ServerMessage) {
    game.host.session.sendSerialized<ServerMessage>(message)
    game.guest.session.sendSerialized<ServerMessage>(message)
}

/** Sends an error message, then closes the connection. */
private suspend fun DefaultWebSocketServerSession.closeWithError(message: String) {
    sendSerialized<ServerMessage>(ServerMessage.LobbyError(message))
    close(CloseReason(CloseReason.Codes.NORMAL, message))
}
