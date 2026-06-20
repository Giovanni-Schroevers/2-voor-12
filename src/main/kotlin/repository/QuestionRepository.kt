package com.example.repository

import com.example.model.Question
import com.example.model.QuestionRecord
import com.example.model.QuestionSummary
import com.example.model.QuestionType
import com.example.model.RoundInventory

interface QuestionRepository {
    suspend fun create(question: Question): UInt
    suspend fun read(id: UInt): Question?
    suspend fun findAll(): List<QuestionRecord>
    suspend fun update(id: UInt, question: Question): Boolean
    suspend fun delete(id: UInt): Boolean
    suspend fun summarize(): QuestionSummary

    /** Snapshot of letters the bank can supply, used to build solo rounds. */
    suspend fun roundInventory(): RoundInventory

    /**
     * Picks one random question matching [letter] and [type], optionally filtered
     * by [category], whose id is not in [exclude]. Returns null when nothing matches.
     */
    suspend fun randomByLetter(
        letter: String,
        type: QuestionType,
        category: String? = null,
        exclude: Set<UInt> = emptySet(),
    ): QuestionRecord?
}
