package com.vlumos

import io.ktor.server.application.*
import com.vlumos.plugins.*
import kotlinx.coroutines.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused")
// application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {
    configureSerialization()
    configureRouting()
}
