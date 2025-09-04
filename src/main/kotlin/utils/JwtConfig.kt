package com.eynnzerr.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    const val SECRET = "your-secret-key-here" // TODO 在生产环境中应该从环境变量读取
    const val ISSUER = "bandoriscription"
    const val AUDIENCE = "bandoriscription-users"
    const val REALM = "Access to bandoriscription"

    fun generateToken(userId: String): String {
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30天过期
            .sign(Algorithm.HMAC256(SECRET))
    }
}
