package com.example

import io.ktor.server.application.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.util.Date

/**
 * Central JWT configuration shared between the security plugin (which verifies
 * tokens) and the admin auth endpoint (which issues them).
 */
object JwtConfig {
    const val AUDIENCE = "jwt-audience"
    const val ISSUER = "https://jwt-provider-domain/"
    const val REALM = "ktor sample app"

    /** Name of the named auth provider used to protect admin routes. */
    const val ADMIN_PROVIDER = "admin-jwt"

    private val secret: String = System.getenv("JWT_SECRET") ?: "secret"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT
        .require(algorithm)
        .withAudience(AUDIENCE)
        .withIssuer(ISSUER)
        .build()

    /** Issues a signed token. Defaults to a 24 hour validity. */
    fun makeToken(subject: String, validityMs: Long = 24 * 60 * 60 * 1000): String =
        JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withSubject(subject)
            .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
            .sign(algorithm)
}

fun Application.configureSecurity() {
    authentication {
        jwt(JwtConfig.ADMIN_PROVIDER) {
            realm = JwtConfig.REALM
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.audience.contains(JwtConfig.AUDIENCE)) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
