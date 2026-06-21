package com.example.game

import com.example.model.PlayerProfile
import io.ktor.server.websocket.DefaultWebSocketServerSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * A connected player: their live websocket [session] plus the [profile] they sent
 * on connect (name + optional avatar).
 */
class Player(
    val session: DefaultWebSocketServerSession,
    val profile: PlayerProfile,
)

/**
 * One online game lobby: its [code], the [host], and the [guest] who joined (null
 * until someone does). Sessions are the server-side websocket type so we can push
 * messages to either player from anywhere.
 */
class Lobby(
    val code: String,
    val host: Player,
) {
    var guest: Player? = null
        private set

    /** The running game, set once the host starts it; null while still in the lobby. */
    var game: GameSession? = null
        private set

    /**
     * Seats [player] as the guest, returning false if the lobby is already full.
     * Synchronized so two racing joiners can't both take the one guest slot.
     */
    @Synchronized
    fun tryJoin(player: Player): Boolean {
        if (guest != null) return false
        guest = player
        return true
    }

    /**
     * Starts the game, returning the new [GameSession], or null if a guest has not
     * joined yet or the game was already started.
     */
    @Synchronized
    fun startGame(): GameSession? {
        if (game != null) return null
        val guest = guest ?: return null
        return GameSession(host, guest).also { game = it }
    }

    /** Frees the guest slot if [session] was the guest (e.g. they disconnected). */
    @Synchronized
    fun removeGuest(session: DefaultWebSocketServerSession) {
        if (guest?.session === session) guest = null
    }
}

/** The outcome of trying to join a lobby by code. */
sealed interface JoinResult {
    data class Success(val lobby: Lobby) : JoinResult
    data object NotFound : JoinResult
    data object Full : JoinResult
}

/**
 * Holds the lobbies that currently exist, keyed by their join code. Lives for the
 * whole app (one shared instance) because lobbies must outlive any single request
 * and be reachable by a second player who connects later.
 *
 * Backed by a [ConcurrentHashMap] since multiple connections touch it at once.
 */
class LobbyManager {

    private val lobbies = ConcurrentHashMap<String, Lobby>()

    /** Creates a lobby with a fresh unique code and registers it. */
    fun create(host: Player): Lobby {
        while (true) {
            val lobby = Lobby(code = randomCode(), host = host)
            // putIfAbsent returns null only when the code was free, so this also
            // guarantees the generated code is unique without a race.
            if (lobbies.putIfAbsent(lobby.code, lobby) == null) return lobby
        }
    }

    /** Attempts to seat [guest] in the lobby with [code]. */
    fun join(code: String, guest: Player): JoinResult {
        val lobby = lobbies[code.uppercase()] ?: return JoinResult.NotFound
        return if (lobby.tryJoin(guest)) JoinResult.Success(lobby) else JoinResult.Full
    }

    /** Forgets a lobby (e.g. when the host disconnects). */
    fun remove(code: String) {
        lobbies.remove(code)
    }

    private fun randomCode(): String = buildString {
        repeat(CODE_LENGTH) { append(CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)]) }
    }

    private companion object {
        const val CODE_LENGTH = 4

        // No I/O/0/1 so codes are easy to read out loud and type.
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
