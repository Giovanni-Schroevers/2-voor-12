package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Whether both players answer the same quiz or each gets their own. */
@Serializable
enum class QuizMode {
    @SerialName("same")
    SAME,

    @SerialName("different")
    DIFFERENT,
}

/**
 * A message a client sends to the server over the game websocket.
 *
 * Like [ServerMessage] this is a sealed hierarchy tagged with a `"type"` field,
 * so the server can switch on the kind. We start with starting a game and add
 * more as gameplay grows (answering a question, etc.).
 *
 * Example wire format: `{"type":"start_game","quizMode":"same","puzzle":"random"}`
 */
@Serializable
sealed interface ClientMessage {

    /**
     * Sent by the host to begin the match. [quizMode] decides whether both players
     * share one quiz or get separate ones; [puzzle] is the puzzle preference, the
     * same option used by the solo game.
     */
    @Serializable
    @SerialName("start_game")
    data class StartGame(
        val quizMode: QuizMode,
        val puzzle: PuzzlePreference,
    ) : ClientMessage

    /**
     * Sent by a player when they have answered all twelve questions. Once both
     * players have sent this (or the answering timer runs out), both advance to
     * the twelve-letter-word phase together.
     */
    @Serializable
    @SerialName("finished_answering")
    data object FinishedAnswering : ClientMessage

    /**
     * Sent by a player with their final [score]. Once both players have submitted,
     * the game is over and the server reports the result.
     */
    @Serializable
    @SerialName("submit_score")
    data class SubmitScore(val score: Int) : ClientMessage
}
