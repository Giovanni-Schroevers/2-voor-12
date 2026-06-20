package com.example.service

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

/** The LLM's reply when asked for a twaalfletterwoord: a single Dutch 12-letter word. */
@Serializable
@LLMDescription("A single existing Dutch word of exactly twelve letters, to be used as the twaalfletterwoord of a 2 voor 12 round.")
data class GeneratedWord(
    @property:LLMDescription("The Dutch word, exactly twelve letters, uppercase, letters A-Z only (no spaces, hyphens or accents).")
    val word: String,
)
