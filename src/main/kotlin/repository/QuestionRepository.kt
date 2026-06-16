package com.example.repository

import com.example.model.Question
import com.example.model.QuestionRecord
import com.example.model.QuestionSummary

interface QuestionRepository {
    suspend fun create(question: Question): UInt
    suspend fun read(id: UInt): Question?
    suspend fun findAll(): List<QuestionRecord>
    suspend fun update(id: UInt, question: Question): Boolean
    suspend fun delete(id: UInt): Boolean
    suspend fun summarize(): QuestionSummary
}
