package com.eynnzerr.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import java.util.*

object JwtConfig {
    val SECRET = System.getenv("JWT_SECRET") ?: throw IllegalStateException("JWT_SECRET environment variable not set.")
    lateinit var ISSUER: String
    lateinit var AUDIENCE: String
    lateinit var REALM: String
    const val VALIDITY_IN_MS = 15L * 24 * 60 * 60 * 1000 // 15 days

    fun init(config: ApplicationConfig) {
        ISSUER = config.property("jwt.issuer").getString()
        AUDIENCE = config.property("jwt.audience").getString()
        REALM = config.property("jwt.realm").getString()
    }

    fun generateToken(userId: String): String {
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_IN_MS))
            .sign(Algorithm.HMAC512(SECRET))
    }
}
