package com.eynnzerr.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register((RateLimitName("api"))) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)
            requestKey { call ->
                call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString() ?: call.request.origin.remoteHost
            }
        }
    }
}
