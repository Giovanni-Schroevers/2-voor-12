package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A message the server pushes to a client over the game websocket.
 *
 * This is a sealed hierarchy so every message is one of a known set of kinds.
 * kotlinx.serialization tags each one with a `"type"` field in the JSON (set by
 * [SerialName]), so the client can switch on it. We start with one kind and add
 * more as the online game grows (player joined, next question, etc.).
 *
 * Example wire format: `{"type":"lobby_created","code":"K7Q2"}`
 */
@Serializable
sealed interface ServerMessage {

    /** Sent to the host right after they connect: the code others use to join. */
    @Serializable
    @SerialName("lobby_created")
    data class LobbyCreated(val code: String) : ServerMessage

    /**
     * Sent to a joining player once accepted, carrying the [opponent] (the host)
     * so the joiner can show who they are up against.
     */
    @Serializable
    @SerialName("joined_lobby")
    data class JoinedLobby(val code: String, val opponent: PlayerProfile) : ServerMessage

    /**
     * Pushed to the host when the second player joins, carrying the [opponent]
     * (the guest) so the host can show who joined.
     */
    @Serializable
    @SerialName("player_joined")
    data class PlayerJoined(val opponent: PlayerProfile) : ServerMessage

    /**
     * Pushed to a player when the game begins, carrying the [round] they should
     * play (the twaalfletterwoord and its twelve questions). In "same" mode both
     * players receive an identical round; in "different" mode each gets their own.
     */
    @Serializable
    @SerialName("game_started")
    data class GameStarted(val round: SoloRound) : ServerMessage

    /**
     * Pushed to both players at the same moment when the answering phase ends —
     * either because both finished or the timer ran out — telling them to advance
     * to the twelve-letter-word phase.
     */
    @Serializable
    @SerialName("proceed_to_word")
    data object ProceedToWord : ServerMessage

    /**
     * Pushed to both players once both have submitted a score, carrying the
     * [result] for the end screen. The server closes the connection afterwards.
     */
    @Serializable
    @SerialName("game_finished")
    data class GameFinished(val result: GameResult) : ServerMessage

    /** Sent before the server closes a connection it cannot accept. */
    @Serializable
    @SerialName("error")
    data class LobbyError(val message: String) : ServerMessage
}
