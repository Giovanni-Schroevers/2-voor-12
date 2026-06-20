package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * Application entry module: installs plugins in order, then hands the database to
 * the composition root. This is the single place that wires the app together.
 */
suspend fun Application.module() {
    configureHttp()
    configureSerialization()
    configureSecurity()
    val database = configureExposed()
    configureWebsockets()
    configureApp(database)
}
