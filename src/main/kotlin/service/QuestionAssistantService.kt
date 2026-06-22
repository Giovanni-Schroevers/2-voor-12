package com.example.service

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.model.executeStructured

/**
 * Uses the Gemini-backed LLM to draft a [QuestionDraft] from a natural-language
 * description. The draft is returned for the editor to review and then save.
 */
class QuestionAssistantService(apiKey: String) {

    private val executor = simpleGoogleAIExecutor(apiKey)

    suspend fun draftQuestion(description: String): QuestionDraft {
        val response = executor.executeStructured<QuestionDraft>(
            prompt = prompt("question-draft") {
                system(SYSTEM_PROMPT)
                user(description)
            },
            model = GoogleModels.Gemini2_5Flash,
        )
        return response.getOrThrow().data
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You help an editor create questions for the Dutch quiz game 2 voor 12 (Twee voor twaalf).
            A round has 12 questions; each correct answer contributes one letter, and the 12 letters
            together form a twaalfletterwoord (twelve-letter word).

            Rules for a well-formed question:
            - type is REGULAR, PAARDENSPRONG, or TAARTPUZZEL.
            - correctLetter is a single uppercase letter, usually the first letter of the answer.
            - REGULAR questions have questionText and no puzzle data.
            - PAARDENSPRONG includes the 8 outer cells of a 3x3 grid (center omitted), so exactly 8 letters; its answer is an 8-letter word.
            - TAARTPUZZEL: its answer is a 9-letter word; set missingIndex to the 0-based position
              of the hidden letter in the answer and pick a reading direction.

            Produce exactly one question that matches the editor's request.
        """.trimIndent()
    }
}
