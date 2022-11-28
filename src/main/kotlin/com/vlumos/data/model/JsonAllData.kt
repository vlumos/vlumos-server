package com.vlumos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class JsonAllData (
    val timestamp: Long,
    var serverData: String,
    val data: String,
    val readMessage: Boolean) {
}