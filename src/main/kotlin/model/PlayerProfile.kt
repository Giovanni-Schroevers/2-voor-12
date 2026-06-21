package com.example.model

import kotlinx.serialization.Serializable

/**
 * The public identity of a player in an online lobby, supplied by the client when
 * they connect. [username] is always present (players are authenticated in-app);
 * [avatar] is optional since an account need not have one.
 *
 * This is self-declared (not verified against the account server-side) — an
 * acceptable trade-off given lobbies are private and joined by a shared code.
 */
@Serializable
data class PlayerProfile(
    val username: String,
    val avatar: String? = null,
)
