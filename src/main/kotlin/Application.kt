package com.eynnzerr

import com.eynnzerr.plugins.configureAuthentication
import com.eynnzerr.plugins.configureDatabases
import com.eynnzerr.plugins.configureFrameworks
import com.eynnzerr.plugins.configureHTTP
import com.eynnzerr.plugins.configureMonitoring
import com.eynnzerr.plugins.configureSerialization
import com.eynnzerr.plugins.configureWebSockets
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureMonitoring()
    configureAuthentication()
    configureWebSockets()
    configureSerialization()
    configureDatabases()
    configureFrameworks()
    configureHTTP()
    configureRouting()
}
