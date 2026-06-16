package com.example.service

import com.example.model.Question
import com.example.model.QuestionRecord
import com.example.model.QuestionSummary
import com.example.repository.QuestionRepository

class QuestionService(private val repository: QuestionRepository) {

    suspend fun create(question: Question): UInt = repository.create(question)

    suspend fun get(id: UInt): Question? = repository.read(id)

    suspend fun list(): List<QuestionRecord> = repository.findAll()

    suspend fun update(id: UInt, question: Question): Boolean = repository.update(id, question)

    suspend fun delete(id: UInt): Boolean = repository.delete(id)

    suspend fun summary(): QuestionSummary = repository.summarize()
}
