package com.example.model

import kotlinx.serialization.Serializable

/**
 * A persisted [Question] together with its database id.
 *
 * Used for listing/management views, where the client needs the id to act on a
 * specific question. The [Question] domain model itself stays id-free.
 */
@Serializable
data class QuestionRecord(
    val id: UInt,
    val question: Question,
)
