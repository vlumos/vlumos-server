package com.vlumos.plugins

import com.vlumos.data.model.AllData
import com.vlumos.plugins.routes.allData
import com.vlumos.plugins.routes.websocketRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route(AllData.path) {
            allData()
        }
        
        val usingWebsockets = System.getenv("usingWebsockets") == "1"
        if (usingWebsockets) {
            websocketRoute()
        }
    }
}

