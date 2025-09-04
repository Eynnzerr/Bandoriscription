package com.eynnzerr.plugins

import com.eynnzerr.data.DatabaseFactory
import io.ktor.server.application.*

fun Application.configureDatabases() {
    DatabaseFactory.init(environment.config)
}
