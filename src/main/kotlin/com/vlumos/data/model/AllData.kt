package com.vlumos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AllData (
    val timestamp: Long,
    var serverData: String,
    val data: String,
    val readMessage: Boolean) {

    companion object {
        const val path = "/allData"
    }
}