package com.vlumos.plugins

import com.vlumos.data.model.AllData
import com.vlumos.plugins.routes.allData
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route(AllData.path) {
            allData()
        }
    }
}

