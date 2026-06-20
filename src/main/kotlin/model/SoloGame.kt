package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The puzzle question a player wants in their solo round. One of the twelve
 * questions (never the last) is replaced by a question of this type; [RANDOM]
 * lets the server pick either puzzle type. Every round contains exactly one puzzle.
 */
@Serializable
enum class PuzzlePreference {
    @SerialName("taartpuzzel")
    TAARTPUZZEL,

    @SerialName("paardensprong")
    PAARDENSPRONG,

    @SerialName("random")
    RANDOM;

    /** The concrete puzzle [QuestionType] for this round; [RANDOM] picks one at random. */
    fun resolveType(): QuestionType = when (this) {
        TAARTPUZZEL -> QuestionType.TAARTPUZZEL
        PAARDENSPRONG -> QuestionType.PAARDENSPRONG
        RANDOM -> listOf(QuestionType.PAARDENSPRONG, QuestionType.TAARTPUZZEL).random()
    }
}

/**
 * A generated solo round: the twaalfletterwoord and the twelve questions whose
 * [Question.correctLetter] values, in order, spell that word.
 *
 * The questions carry their full data including answers — solo play is
 * client-authoritative, so the client runs its own timer and scoring. Online
 * play must NOT reuse this shape; it keeps answers server-side.
 */
@Serializable
data class SoloRound(
    val word: String,
    val questions: List<Question>,
)

/**
 * A snapshot of which letters the question bank can currently supply, used both
 * to steer word generation and to check a candidate word is satisfiable.
 *
 * [regularByLetter] counts REGULAR questions per uppercase letter (so words with
 * repeated letters can be checked for enough distinct questions). [musicLetters]
 * are letters that have at least one REGULAR question in the music category
 * (eligible for the mandatory final slot). [paardensprongLetters] and
 * [taartpuzzelLetters] are letters with at least one question of that puzzle type.
 */
@Serializable
data class RoundInventory(
    val regularByLetter: Map<String, Int>,
    val musicLetters: Set<String>,
    val paardensprongLetters: Set<String>,
    val taartpuzzelLetters: Set<String>,
) {
    /** Letters with at least one question of [type] (any category). */
    fun lettersFor(type: QuestionType): Set<String> = when (type) {
        QuestionType.REGULAR -> regularByLetter.keys
        QuestionType.PAARDENSPRONG -> paardensprongLetters
        QuestionType.TAARTPUZZEL -> taartpuzzelLetters
    }
}
