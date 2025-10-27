package com.eynnzerr

import com.eynnzerr.routes.authRoutes
import com.eynnzerr.routes.roomRoutes
import com.eynnzerr.routes.webSocketRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK)
        }
        rateLimit(RateLimitName("api")) {
            route("/bandori/api") {
                authRoutes()
                roomRoutes()
            }
            route("/bandori/ws") {
                webSocketRoutes()
            }
        }
    }
}
