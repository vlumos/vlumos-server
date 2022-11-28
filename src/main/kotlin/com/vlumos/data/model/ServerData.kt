package com.vlumos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerData (
    val wrappedSecret: String,
    val iv: String,
    val data: String
)