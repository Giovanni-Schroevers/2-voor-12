package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single player's outcome: who they are and the score they reported. */
@Serializable
data class PlayerResult(
    val profile: PlayerProfile,
    val score: Int,
)

/** Who won the match. */
@Serializable
enum class GameOutcome {
    @SerialName("host_won")
    HOST_WON,

    @SerialName("guest_won")
    GUEST_WON,

    @SerialName("tie")
    TIE,
}

/** The final result of a finished game, used to render the end screen. */
@Serializable
data class GameResult(
    val host: PlayerResult,
    val guest: PlayerResult,
    val outcome: GameOutcome,
)
