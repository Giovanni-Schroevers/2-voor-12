package com.example.routing

import com.example.game.LobbyManager
import com.example.model.ClientMessage
import com.example.model.GameOutcome
import com.example.model.PlayerProfile
import com.example.model.PuzzlePreference
import com.example.model.QuizMode
import com.example.model.ServerMessage
import com.example.model.SoloRound
import com.example.service.RoundGenerator
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class GameSocketTest {

    @Test
    fun `host receives a lobby code on connect`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") {
            val message = receiveMessage()
            assertTrue(message is ServerMessage.LobbyCreated, "expected a LobbyCreated message")
            assertEquals(4, message.code.length, "code should be 4 characters")
        }
    }

    @Test
    fun `connecting without a username is rejected`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby") {
            val message = receiveMessage()
            assertTrue(message is ServerMessage.LobbyError, "expected a LobbyError when username is missing")
        }
    }

    @Test
    fun `second player joins and both sides learn the opponent profile`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob&avatar=bob-avatar") { // host (has an avatar)
            val created = receiveMessage()
            assertTrue(created is ServerMessage.LobbyCreated)
            val code = created.code

            client.webSocket("/ws/lobby?code=$code&username=Alice") { // guest (no avatar)
                val joined = receiveMessage()
                assertTrue(joined is ServerMessage.JoinedLobby, "guest should be confirmed")
                assertEquals(code, joined.code)
                assertEquals("Bob", joined.opponent.username)
                assertEquals("bob-avatar", joined.opponent.avatar)
            }

            val hostNotice = receiveMessage()
            assertTrue(hostNotice is ServerMessage.PlayerJoined, "host should hear PlayerJoined")
            assertEquals("Alice", hostNotice.opponent.username)
            assertNull(hostNotice.opponent.avatar, "guest joined without an avatar")
        }
    }

    @Test
    fun `joining an unknown code returns an error`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?code=ZZZZ&username=Alice") {
            val message = receiveMessage()
            assertTrue(message is ServerMessage.LobbyError, "expected a LobbyError")
        }
    }

    @Test
    fun `host starts a same-quiz game and both players get the one round`() = testApplication {
        val rounds = FakeRoundGenerator(testRound)
        installGameSocket(rounds)
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") { // host
            val host = this
            val created = receiveMessage() as ServerMessage.LobbyCreated

            client.webSocket("/ws/lobby?code=${created.code}&username=Alice") { // guest
                receiveMessage() // JoinedLobby
                host.sendMessage(ClientMessage.StartGame(QuizMode.SAME, PuzzlePreference.RANDOM))

                val guestStart = receiveMessage()
                assertTrue(guestStart is ServerMessage.GameStarted, "guest should receive GameStarted")
                assertEquals(testRound.word, guestStart.round.word)
            }

            val playerJoined = receiveMessage()
            assertTrue(playerJoined is ServerMessage.PlayerJoined)
            val hostStart = receiveMessage()
            assertTrue(hostStart is ServerMessage.GameStarted, "host should receive GameStarted")
        }

        assertEquals(1, rounds.calls, "same-quiz mode should generate exactly one round")
    }

    @Test
    fun `host starts a different-quiz game and two rounds are generated`() = testApplication {
        val rounds = FakeRoundGenerator(testRound)
        installGameSocket(rounds)
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") { // host
            val host = this
            val created = receiveMessage() as ServerMessage.LobbyCreated

            client.webSocket("/ws/lobby?code=${created.code}&username=Alice") { // guest
                receiveMessage() // JoinedLobby
                host.sendMessage(ClientMessage.StartGame(QuizMode.DIFFERENT, PuzzlePreference.PAARDENSPRONG))

                val guestStart = receiveMessage()
                assertTrue(guestStart is ServerMessage.GameStarted, "guest should receive GameStarted")
            }

            receiveMessage() // PlayerJoined
            val hostStart = receiveMessage()
            assertTrue(hostStart is ServerMessage.GameStarted, "host should receive GameStarted")
        }

        assertEquals(2, rounds.calls, "different-quiz mode should generate one round per player")
    }

    @Test
    fun `starting before a player joins returns an error`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") {
            receiveMessage() // LobbyCreated
            sendMessage(ClientMessage.StartGame(QuizMode.SAME, PuzzlePreference.RANDOM))

            val message = receiveMessage()
            assertTrue(message is ServerMessage.LobbyError, "expected an error when no one has joined")
        }
    }

    @Test
    fun `both finishing answering advances both players to the word phase`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") { // host
            val host = this
            val created = receiveMessage() as ServerMessage.LobbyCreated

            client.webSocket("/ws/lobby?code=${created.code}&username=Alice") { // guest
                receiveMessage() // JoinedLobby
                host.sendMessage(ClientMessage.StartGame(QuizMode.SAME, PuzzlePreference.RANDOM))
                receiveMessage() // GameStarted (guest)

                // Both report finished; only then should the word phase begin.
                host.sendMessage(ClientMessage.FinishedAnswering)
                sendMessage(ClientMessage.FinishedAnswering)

                val guestProceed = receiveMessage()
                assertTrue(guestProceed is ServerMessage.ProceedToWord, "guest should be advanced")
            }

            receiveMessage() // PlayerJoined
            receiveMessage() // GameStarted (host)
            val hostProceed = receiveMessage()
            assertTrue(hostProceed is ServerMessage.ProceedToWord, "host should be advanced")
        }
    }

    @Test
    fun `the timer advances both players when answering time runs out`() = testApplication {
        installGameSocket(answeringTimeout = 150.milliseconds)
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") { // host
            val host = this
            val created = receiveMessage() as ServerMessage.LobbyCreated

            client.webSocket("/ws/lobby?code=${created.code}&username=Alice") { // guest
                receiveMessage() // JoinedLobby
                host.sendMessage(ClientMessage.StartGame(QuizMode.SAME, PuzzlePreference.RANDOM))
                receiveMessage() // GameStarted (guest)

                // Nobody finishes; the timer should advance everyone on its own.
                val guestProceed = receiveMessage()
                assertTrue(guestProceed is ServerMessage.ProceedToWord, "timer should advance the guest")
            }

            receiveMessage() // PlayerJoined
            receiveMessage() // GameStarted (host)
            val hostProceed = receiveMessage()
            assertTrue(hostProceed is ServerMessage.ProceedToWord, "timer should advance the host")
        }
    }

    @Test
    fun `both submitting scores finishes the game with a winner`() = testApplication {
        installGameSocket()
        val client = createClient { install(WebSockets) }

        client.webSocket("/ws/lobby?username=Bob") { // host
            val host = this
            val created = receiveMessage() as ServerMessage.LobbyCreated

            client.webSocket("/ws/lobby?code=${created.code}&username=Alice") { // guest
                receiveMessage() // JoinedLobby
                host.sendMessage(ClientMessage.StartGame(QuizMode.SAME, PuzzlePreference.RANDOM))
                receiveMessage() // GameStarted (guest)

                host.sendMessage(ClientMessage.SubmitScore(100))
                sendMessage(ClientMessage.SubmitScore(70))

                val guestFinished = receiveMessage()
                assertTrue(guestFinished is ServerMessage.GameFinished, "guest should get the result")
                assertEquals(GameOutcome.HOST_WON, guestFinished.result.outcome)
                assertEquals(100, guestFinished.result.host.score)
                assertEquals(70, guestFinished.result.guest.score)
            }

            receiveMessage() // PlayerJoined
            receiveMessage() // GameStarted (host)
            val hostFinished = receiveMessage()
            assertTrue(hostFinished is ServerMessage.GameFinished, "host should get the result")
            assertEquals(GameOutcome.HOST_WON, hostFinished.result.outcome)
        }
    }
}

/** A fixed round, with no real questions — the lobby tests only care it is delivered. */
private val testRound = SoloRound(word = "TESTWOORDXYZ", questions = emptyList())

/** Returns [round] for every request and counts how many rounds were asked for. */
private class FakeRoundGenerator(private val round: SoloRound) : RoundGenerator {
    var calls = 0
        private set

    override suspend fun generateRound(preference: PuzzlePreference): SoloRound {
        calls++
        return round
    }
}

/** Installs server websockets + the game routes with a fresh in-memory lobby store. */
private fun ApplicationTestBuilder.installGameSocket(
    rounds: RoundGenerator = FakeRoundGenerator(testRound),
    answeringTimeout: Duration = 15.minutes,
) {
    install(io.ktor.server.websocket.WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    routing { gameSocketRoutes(LobbyManager(), rounds, answeringTimeout) }
}

/** Reads the next text frame and decodes it as a [ServerMessage]. */
private suspend fun DefaultClientWebSocketSession.receiveMessage(): ServerMessage {
    val frame = incoming.receive() as Frame.Text
    return Json.decodeFromString<ServerMessage>(frame.readText())
}

/** Sends a [ClientMessage] as a JSON text frame (tagged with its "type"). */
private suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) {
    send(Frame.Text(Json.encodeToString(ClientMessage.serializer(), message)))
}
