package com.example.service

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.model.executeStructured
import com.example.model.QuestionType
import com.example.model.RoundInventory

/** Supplies a candidate twaalfletterwoord for a solo round. */
interface WordGenerator {
    suspend fun generateWord(
        inventory: RoundInventory,
        puzzleType: QuestionType,
        feedback: String? = null,
    ): String
}

/**
 * Uses the Gemini-backed LLM to pick a Dutch twaalfletterwoord that the current
 * question bank can actually back. The [inventory] of available letters is fed
 * into the prompt so the model only proposes spellable words; [feedback] lets the
 * caller nudge a retry after a candidate turned out to be unsatisfiable.
 */
class WordService(apiKey: String) : WordGenerator {

    private val executor = simpleGoogleAIExecutor(apiKey)

    override suspend fun generateWord(
        inventory: RoundInventory,
        puzzleType: QuestionType,
        feedback: String?,
    ): String {
        val response = executor.executeStructured<GeneratedWord>(
            prompt = prompt("twaalfletterwoord") {
                system(SYSTEM_PROMPT)
                user(buildUserPrompt(inventory, puzzleType, feedback))
            },
            model = GoogleModels.Gemini2_5Flash,
        )
        return response.getOrThrow().data.word.trim().uppercase()
    }

    private fun buildUserPrompt(
        inventory: RoundInventory,
        puzzleType: QuestionType,
        feedback: String?,
    ): String = buildString {
        appendLine("Choose one existing Dutch word of exactly twelve letters (A-Z, no spaces or accents).")
        appendLine()
        appendLine("The round is built from our question bank, so the word MUST satisfy all of these:")
        appendLine("- Every letter of the word must appear in the list of available letters below.")
        appendLine("- If a letter repeats in the word, the bank must have at least that many questions for it.")
        appendLine("- The TWELFTH (last) letter must be one of the music letters below.")
        appendLine("- At least one of the FIRST ELEVEN letters must be one of the ${puzzleType.name.lowercase()} letters below.")
        appendLine("  (${puzzleType.name} questions exist only for those letters.)")
        appendLine()
        appendLine("Available regular letters with how many questions each has:")
        appendLine(inventory.regularByLetter.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}:${it.value}" })
        appendLine("Music letters (eligible for the last slot): ${inventory.musicLetters.sorted().joinToString(", ")}")
        val puzzleLetters = inventory.lettersFor(puzzleType).sorted().joinToString(", ")
        appendLine("${puzzleType.name.lowercase().replaceFirstChar { it.uppercase() }} letters: $puzzleLetters")
        if (feedback != null) {
            appendLine()
            appendLine("Your previous answer did not work: $feedback")
            appendLine("Pick a different word that satisfies every rule above.")
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You generate the twaalfletterwoord (twelve-letter word) for the Dutch quiz game
            2 voor 12 (Twee voor twaalf). You return exactly one real, existing Dutch word of
            exactly twelve letters. The word is spelled by the first letters contributed by
            twelve quiz questions, so it must be buildable from the available letters you are given.
            Always obey every constraint in the user's message.
        """.trimIndent()
    }
}
