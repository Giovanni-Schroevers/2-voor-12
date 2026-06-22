package com.example.service

import ai.koog.agents.core.tools.annotations.LLMDescription
import com.example.model.PaardensprongPuzzle
import com.example.model.QuestionType
import com.example.model.TaartpuzzelPuzzle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An LLM-generated draft of a question. Its JSON shape matches the domain
 * [com.example.model.Question], so a reviewed draft can be saved as-is via the
 * create endpoint. The [LLMDescription] annotations guide the model; validation
 * is deliberately left to the domain model on save.
 */
@Serializable
@SerialName("Question")
@LLMDescription("A single 2 voor 12 quiz question. Each correct answer contributes one letter to the twaalfletterwoord (the twelve-letter word).")
data class QuestionDraft(
    @property:LLMDescription("Question kind: REGULAR, PAARDENSPRONG, or TAARTPUZZEL.")
    val type: QuestionType,
    @property:LLMDescription("Subject/category, e.g. 'Geschiedenis'.")
    val category: String,
    @property:LLMDescription("The correct answer to the question.")
    val correctAnswer: String,
    @property:LLMDescription("The single letter this answer contributes to the twaalfletterwoord; usually the first letter of the answer.")
    val correctLetter: String,
    @property:LLMDescription("The question prompt. Required for REGULAR questions; leave null for puzzle types.")
    val questionText: String? = null,
    @property:LLMDescription("Only for PAARDENSPRONG questions: the 8 outer cells of the 3x3 grid, row by row (the unused center cell is omitted), so exactly 8 letters.")
    val paardensprong: PaardensprongPuzzle? = null,
    @property:LLMDescription("Only for TAARTPUZZEL questions: missingIndex is the 0-based position in the answer that is hidden, plus a reading direction (CLOCKWISE or COUNTERCLOCKWISE).")
    val taartpuzzel: TaartpuzzelPuzzle? = null,
)
