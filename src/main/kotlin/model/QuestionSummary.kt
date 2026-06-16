package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class QuestionSummary(
    val total: Int,
    val byCorrectLetter: Map<String, Int>,
    val byCategory: Map<String, Int>,
    val byType: Map<QuestionType, Int>,
)
