package com.example.service

import com.example.model.PuzzlePreference
import com.example.model.Question
import com.example.model.QuestionType
import com.example.model.RoundInventory
import com.example.model.SoloRound
import com.example.repository.QuestionRepository

/** Thrown when no satisfiable round could be generated for the current bank. */
class UnsatisfiableRoundException(message: String) : RuntimeException(message)

/** Produces a playable round for a given puzzle preference. */
interface RoundGenerator {
    suspend fun generateRound(preference: PuzzlePreference): SoloRound
}

/**
 * Builds a solo [SoloRound]: a twaalfletterwoord plus the twelve questions that
 * spell it. The last question is always a regular music question; one of the first
 * eleven is replaced by a puzzle question (paardensprong or taartpuzzel).
 *
 * Word generation is inventory-constrained ([WordService]) but the LLM can still
 * propose a word the bank cannot fully back, so a candidate is validated and the
 * round assembled with distinct questions; on failure we retry with feedback up
 * to [maxAttempts] times before giving up with [UnsatisfiableRoundException].
 */
class SoloGameService(
    private val repository: QuestionRepository,
    private val wordService: WordGenerator,
    private val maxAttempts: Int = 5,
) : RoundGenerator {

    override suspend fun generateRound(preference: PuzzlePreference): SoloRound {
        val inventory = repository.roundInventory()
        val puzzleType = preference.resolveType()
        var feedback: String? = null

        repeat(maxAttempts) {
            val word = wordService.generateWord(inventory, puzzleType, feedback)

            val problem = validate(word, puzzleType, inventory)
            if (problem != null) {
                feedback = problem
                return@repeat
            }

            val round = assemble(word, puzzleType)
            if (round != null) return round
            feedback = "Could not find enough distinct questions in the bank to back '$word'."
        }

        throw UnsatisfiableRoundException(
            "Could not generate a solo round for puzzle '$puzzleType' after $maxAttempts attempts: ${feedback ?: "unknown reason"}"
        )
    }

    /** Cheap structural checks; returns an error message for retry feedback, or null when ok. */
    private fun validate(word: String, puzzleType: QuestionType, inventory: RoundInventory): String? {
        if (word.length != WORD_LENGTH) return "Word must be exactly $WORD_LENGTH letters, but '$word' has ${word.length}."
        if (!word.all { it in 'A'..'Z' }) return "Word '$word' must contain only the letters A-Z."

        val letters = word.map { it.toString() }
        if (letters.last() !in inventory.musicLetters) {
            return "The last letter '${letters.last()}' has no regular music question available."
        }

        val puzzleLetters = inventory.lettersFor(puzzleType)
        if (letters.take(WORD_LENGTH - 1).none { it in puzzleLetters }) {
            return "None of the first ${WORD_LENGTH - 1} letters has a ${puzzleType.name} question available."
        }
        return null
    }

    /** Picks distinct questions for every slot, or null if the bank runs short. */
    private suspend fun assemble(word: String, puzzleType: QuestionType): SoloRound? {
        val letters = word.map { it.toString() }
        val used = mutableSetOf<UInt>()
        val slots = arrayOfNulls<Question>(WORD_LENGTH)
        val lastIndex = WORD_LENGTH - 1

        // The final slot is always a regular question in the music category.
        val music = repository.randomByLetter(letters[lastIndex], QuestionType.REGULAR, MUSIC_CATEGORY, used)
            ?: return null
        used += music.id
        slots[lastIndex] = music.question

        // The puzzle replaces one random non-final slot whose letter can supply it.
        var placed = false
        for (pos in (0 until lastIndex).shuffled()) {
            val pick = repository.randomByLetter(letters[pos], puzzleType, exclude = used) ?: continue
            used += pick.id
            slots[pos] = pick.question
            placed = true
            break
        }
        if (!placed) return null

        // Everything else is a regular question for its letter.
        for (i in 0 until WORD_LENGTH) {
            if (slots[i] != null) continue
            val pick = repository.randomByLetter(letters[i], QuestionType.REGULAR, exclude = used) ?: return null
            used += pick.id
            slots[i] = pick.question
        }

        return SoloRound(word = word, questions = slots.map { it!! })
    }

    private companion object {
        const val WORD_LENGTH = 12
        const val MUSIC_CATEGORY = "Muziek"
    }
}
