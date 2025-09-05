package com.eynnzerr.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.*
import io.ktor.server.plugins.doublereceive.*
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(DoubleReceive)
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val path = call.request.path()
            val headers = call.request.headers.entries().joinToString("\n") { "  ${it.key}: ${it.value.joinToString(", ")}" }
            val body = if (call.request.httpMethod == HttpMethod.Post) {
                try {
                    runBlocking { call.receiveText() }
                } catch (e: Exception) {
                    "Could not log request body: ${e.message}"
                }
            } else {
                ""
            }
            "\nRequest Path: ${path}\nHeaders:\n${headers}\nBody:\n${body}"
        }
    }
}
