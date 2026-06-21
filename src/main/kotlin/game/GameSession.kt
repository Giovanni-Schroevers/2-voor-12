package com.example.game

import com.example.model.GameOutcome
import com.example.model.GameResult
import com.example.model.PlayerResult
import kotlinx.coroutines.Job

/**
 * The mutable state of an in-progress game between [host] and [guest].
 *
 * Two connection coroutines plus a timer coroutine all touch this state, so every
 * mutation is `@Synchronized`. The methods only mutate and return a *decision*
 * (e.g. "now start the word phase"); the actual network sends are done by the
 * caller outside the lock, since sending suspends and must not block the monitor.
 */
class GameSession(
    val host: Player,
    val guest: Player,
) {
    private var hostFinished = false
    private var guestFinished = false
    private var wordPhaseStarted = false

    private var hostScore: Int? = null
    private var guestScore: Int? = null
    private var finished = false

    /** The answering-phase timer; cancelled if both players finish early. */
    var timerJob: Job? = null

    /**
     * Records that a player finished answering. Returns true only the first time
     * both have finished, signalling the caller to start the word phase.
     */
    @Synchronized
    fun finishAnswering(isHost: Boolean): Boolean {
        if (isHost) hostFinished = true else guestFinished = true
        return startWordPhaseIfPending(hostFinished && guestFinished)
    }

    /**
     * Forces the word phase when the answering timer fires. Returns true only the
     * first time the word phase begins (so a late finish won't trigger it twice).
     */
    @Synchronized
    fun startWordPhaseOnTimeout(): Boolean = startWordPhaseIfPending(true)

    private fun startWordPhaseIfPending(condition: Boolean): Boolean {
        if (!condition || wordPhaseStarted) return false
        wordPhaseStarted = true
        return true
    }

    /**
     * Records a player's final [score]. Returns the [GameResult] the first time
     * both players have submitted, otherwise null.
     */
    @Synchronized
    fun submitScore(isHost: Boolean, score: Int): GameResult? {
        if (isHost) hostScore = score else guestScore = score
        if (finished) return null

        val finalHost = hostScore ?: return null
        val finalGuest = guestScore ?: return null
        finished = true

        return GameResult(
            host = PlayerResult(host.profile, finalHost),
            guest = PlayerResult(guest.profile, finalGuest),
            outcome = when {
                finalHost > finalGuest -> GameOutcome.HOST_WON
                finalGuest > finalHost -> GameOutcome.GUEST_WON
                else -> GameOutcome.TIE
            },
        )
    }
}
