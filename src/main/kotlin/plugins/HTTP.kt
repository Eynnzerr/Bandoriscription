package com.eynnzerr.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        val allowedHosts = this@configureHTTP.environment.config.propertyOrNull("cors.allowedHosts")?.getList() ?: emptyList()
        allowedHosts.forEach { host ->
            allowHost(host, schemes = listOf("http", "https"))
        }
    }
}
