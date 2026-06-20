package com.example

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Password hashing using BCrypt (per-hash random salt, tunable work factor).
 * Hashes are self-describing, so [verify] reads the salt and cost from [hash].
 */
object Passwords {
    /** BCrypt work factor; higher is slower and more resistant to brute force. */
    private const val COST = 12

    fun hash(raw: String): String =
        BCrypt.withDefaults().hashToString(COST, raw.toCharArray())

    fun verify(raw: String, hash: String): Boolean =
        BCrypt.verifyer().verify(raw.toCharArray(), hash).verified
}
