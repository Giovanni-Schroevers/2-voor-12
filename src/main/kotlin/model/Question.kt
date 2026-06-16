package com.example.model

import kotlinx.serialization.Serializable

/** The three kinds of questions that appear in a 2 voor 12 round. */
enum class QuestionType { REGULAR, PAARDENSPRONG, TAARTPUZZEL }

/** Reading direction of the taartpuzzel circle. */
enum class Direction { CLOCKWISE, COUNTERCLOCKWISE }

/**
 * Paardensprong (knight's move) detail: a 3x3 grid of letters, stored row-major
 * as 9 characters. Knight's moves through the grid spell the 8-letter answer.
 */
@Serializable
data class PaardensprongPuzzle(
    val grid: String,
)

/**
 * Taartpuzzel (cake puzzle) detail: the answer's letters are arranged around a
 * circle read in [direction], with one slot left blank. [missingIndex] is the
 * 0-based position in the answer that is hidden — stored as a position (not a
 * letter value) so it stays unambiguous when a letter occurs more than once.
 */
@Serializable
data class TaartpuzzelPuzzle(
    val missingIndex: Int,
    val direction: Direction,
)

/**
 * A single question in a round. Common fields live here; puzzle types carry an
 * extra [paardensprong] or [taartpuzzel] payload (persisted in their own tables).
 *
 * [correctLetter] is the letter this answer contributes to the twaalfletterwoord
 * and is stored explicitly, since it is not always the first letter of the answer.
 */
@Serializable
data class Question(
    val type: QuestionType,
    val category: String,
    val correctAnswer: String,
    val correctLetter: String,
    val questionText: String? = null,
    val paardensprong: PaardensprongPuzzle? = null,
    val taartpuzzel: TaartpuzzelPuzzle? = null,
) {
    init {
        require(correctLetter.length == 1) { "correctLetter must be a single character" }
        when (type) {
            QuestionType.REGULAR -> {
                require(questionText != null) { "REGULAR questions require questionText" }
                require(paardensprong == null && taartpuzzel == null) {
                    "REGULAR questions must not carry puzzle data"
                }
            }
            QuestionType.PAARDENSPRONG -> {
                require(paardensprong != null) { "PAARDENSPRONG questions require paardensprong data" }
                require(taartpuzzel == null) { "PAARDENSPRONG questions must not carry taartpuzzel data" }
                require(paardensprong.grid.length == 9) { "paardensprong grid must be 9 letters (3x3)" }
            }
            QuestionType.TAARTPUZZEL -> {
                require(taartpuzzel != null) { "TAARTPUZZEL questions require taartpuzzel data" }
                require(paardensprong == null) { "TAARTPUZZEL questions must not carry paardensprong data" }
                require(taartpuzzel.missingIndex in correctAnswer.indices) {
                    "missingIndex must point at a letter of the answer"
                }
            }
        }
    }
}
