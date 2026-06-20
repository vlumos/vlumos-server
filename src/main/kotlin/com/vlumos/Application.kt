package com.vlumos

import io.ktor.server.application.*
import com.vlumos.plugins.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused")
// application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    configureSerialization()
    configureWebSockets()
    configureRouting()
}
