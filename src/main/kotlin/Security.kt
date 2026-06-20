package com.example

import io.ktor.server.application.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
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

    /** Names of the named auth providers protecting admin and user routes. */
    const val ADMIN_PROVIDER = "admin-jwt"
    const val USER_PROVIDER = "user-jwt"

    /** Role claim and its values, used to tell admin and user tokens apart. */
    private const val ROLE_CLAIM = "role"
    const val ADMIN_ROLE = "admin"
    const val USER_ROLE = "user"

    private val secret: String = System.getenv("JWT_SECRET") ?: "secret"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT
        .require(algorithm)
        .withAudience(AUDIENCE)
        .withIssuer(ISSUER)
        .build()

    /** Issues a signed token carrying [role]. Defaults to a 24 hour validity. */
    fun makeToken(subject: String, role: String, validityMs: Long = 24 * 60 * 60 * 1000): String =
        JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withSubject(subject)
            .withClaim(ROLE_CLAIM, role)
            .withExpiresAt(Date(System.currentTimeMillis() + validityMs))
            .sign(algorithm)

    /** True when the verified [payload] carries the given [role]. */
    fun hasRole(payload: Payload, role: String): Boolean =
        payload.getClaim(ROLE_CLAIM).asString() == role
}

fun Application.configureSecurity() {
    authentication {
        jwt(JwtConfig.ADMIN_PROVIDER) {
            realm = JwtConfig.REALM
            verifier(JwtConfig.verifier)
            validate { credential -> credential.principalForRole(JwtConfig.ADMIN_ROLE) }
        }
        jwt(JwtConfig.USER_PROVIDER) {
            realm = JwtConfig.REALM
            verifier(JwtConfig.verifier)
            validate { credential -> credential.principalForRole(JwtConfig.USER_ROLE) }
        }
    }
}

/** Accepts the token only if it targets this audience and carries [role]. */
private fun JWTCredential.principalForRole(role: String): JWTPrincipal? =
    if (payload.audience.contains(JwtConfig.AUDIENCE) && JwtConfig.hasRole(payload, role)) {
        JWTPrincipal(payload)
    } else {
        null
    }
