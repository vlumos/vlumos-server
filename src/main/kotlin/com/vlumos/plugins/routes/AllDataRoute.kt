package com.vlumos.plugins.routes

import com.vlumos.data.model.AllData
import com.vlumos.data.model.ServerData
import com.vlumos.services.AllDataService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.time.Instant
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.vlumos.data.model.JsonAllData
import org.slf4j.LoggerFactory
import kotlin.reflect.jvm.internal.impl.descriptors.deserialization.PlatformDependentDeclarationFilter.All

private const val RAN_LENGTH = 16
private const val TIME_LENGTH = 10

private val allDataService = AllDataService()

val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
val rootLogger: Logger = loggerContext.getLogger("org.mongodb.driver")

val negativeCheck = System.getenv("checkNegTimeout")?.toIntOrNull() ?: -300
val positiveCheck = System.getenv("checkTimeout")?.toIntOrNull() ?: 300
val storeMessage = System.getenv("storeMessages")?.toBoolean() ?: false
val log = System.getenv("log")?.toBoolean() ?: true



fun Route.allData() {
    rootLogger.level = Level.OFF

    get {
        val amount = call.parameters["amount"]?.toInt() ?: error("Invalid get request")
        val data = call.parameters["data"] ?: error("Invalid get request")
        val unBuildPair = unbuildServerDataGet(data) // huid, timestamp

        val timeDifference = (Instant.now().toEpochMilli() / 1000) - unBuildPair.second.toInt()
        if (log) println("get - $timeDifference")
        if (timeDifference in negativeCheck..positiveCheck) {
            val messagesRequested = if (storeMessage) {
                allDataService.getAllUnreadData(unBuildPair.first, amount)
            } else {
                allDataService.getAllData(unBuildPair.first, amount)
            }

            val uidsFound = mutableListOf<String>()
            val jsonMessagesRequested = mutableListOf<JsonAllData>()
            for (message in messagesRequested) {
                uidsFound.add("'${message.serverData}'")
                message.serverData = ""

                jsonMessagesRequested.add(JsonAllData(
                    message.timestamp.toString().toLong(),
                    message.serverData,
                    message.data,
                    message.readMessage)
                )
            }
            if (uidsFound.isNotEmpty()) {
                if (System.getenv("storeMessages").toBoolean()) {
                    allDataService.updateMarkRead(uidsFound)
                } else {
                    allDataService.deleteManyAllData(uidsFound)
                }
            }
            call.respond(HttpStatusCode.OK, jsonMessagesRequested)
        } else {
            val badResponse: List<JsonAllData> = listOf(JsonAllData(0L, "Timed Out", "ERROR", false))
            call.respond(HttpStatusCode.NotAcceptable, badResponse)
        }
    }

    post {
        val allDataList = call.receive<List<JsonAllData>>()
        var newAllData = AllData(0L, "", "", false)
        var jsonAllData: JsonAllData
        try {
            var counter = 0
            var timestamp = 0L
            for (allData in allDataList) {
                if (allData.data == "" &&  allData.serverData == "") {
                    timestamp = allData.timestamp
                } else if (allData.data == "SERVERKEY") {
                    val publicKeyString = allDataService.publicKey()
                    val publicKeyStringGzip = gzip(publicKeyString).toBase64Url()
                    newAllData = AllData(0L, "", "SERVERKEY$publicKeyStringGzip", false)
                } else {
                    //first allData just get timestamp to use for the rest
                    val unBuildPair = unbuildServerData(allData.serverData)
                    if (counter == 0) {
                        timestamp = unBuildPair.second.toLong()
                    } else {
                        val timeDifference = (Instant.now().toEpochMilli() / 1000) - timestamp //unBuildPair.second.toInt()
                        if (log) println("post - $timeDifference")
                        if (timeDifference in negativeCheck ..positiveCheck) {
                            newAllData = AllData(allData.timestamp, unBuildPair.first, allData.data, false)
                            allDataService.addOneAllData(newAllData)
                        } else {
                            jsonAllData = JsonAllData(0L, "Timed Out", "ERROR", false)
                            call.respond(HttpStatusCode.NotAcceptable, jsonAllData)
                            return@post
                        }
                    }
                }
                counter += 1
            }
            call.respond(HttpStatusCode.OK, newAllData)
        } catch (e: Exception) {
            if (log) println(e.stackTraceToString())
            jsonAllData = JsonAllData(0L, e.stackTraceToString(), "ERROR", false)
            call.respond(HttpStatusCode.Forbidden, jsonAllData)
        }
    }
}

private fun serverData(data: String): ServerData {
    val dataByteArray = data.fromBase64Url()
    val dataUngzip = ungzip(dataByteArray)
    return Json.decodeFromString(dataUngzip)
}

private fun unbuildServerData(data: String): Pair<String, String> {
    val privateKey = allDataService.privateKey()
    val serverDataJson = serverData(data)
    val unwrappedAes = unwrapAes(serverDataJson.wrappedSecret, privateKey)
    val randomHuid = decryptAes(unwrappedAes, serverDataJson.iv.fromBase64(), serverDataJson.data.fromBase64())
    val unRandomHuid = randomHuid.removeRange(0, RAN_LENGTH).trim()
    val timestamp = unRandomHuid.slice( 0..9)
    val unTimestampUid = unRandomHuid.removeRange(0, TIME_LENGTH)
    if (log) println("POST - unTimestampUid = $unTimestampUid, timestamp = $timestamp")
    return Pair(unTimestampUid, timestamp)
}

private fun unbuildServerDataGet(data: String): Pair<String, String> {
    val serverDataJson = serverData(data)
    val uid = decryptRsaData(serverDataJson.data.fromBase64()) //, privateKey)
    val unRandomUid = uid.removeRange(0, RAN_LENGTH).trim()
    val timestamp = unRandomUid.slice( 0..9)
    val unTimestampUid = unRandomUid.removeRange(0, TIME_LENGTH)
    if (log) println("GET - huid = ${sha256(unTimestampUid).toBase64().trim()}, timestamp = $timestamp")
    return Pair(sha256(unTimestampUid).toBase64().trim(), timestamp)
}

fun gzip(content: String): ByteArray {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    return bos.toByteArray()
}

private fun ungzip(content: ByteArray?): String =
    GZIPInputStream(content?.inputStream()).bufferedReader().use { it.readText() }

private fun unwrapAes(aesWrappedKey:String, privateKey: PrivateKey): Key {
    val cipher = Cipher.getInstance( "RSA/ECB/PKCS1PADDING")
    cipher.init(Cipher.UNWRAP_MODE, privateKey)
    return cipher.unwrap(aesWrappedKey.fromBase64(), "AES",  Cipher.SECRET_KEY)
}

private fun decryptAes(key:Key, ivBytes:ByteArray, toDecrypt: ByteArray): String {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    val spec = IvParameterSpec(ivBytes)
    cipher.init(Cipher.DECRYPT_MODE, key, spec)
    return cipher.doFinal(toDecrypt).decodeToString()
}

private fun decryptRsaData(encryption: ByteArray): String { // , privateKey: PrivateKey
    val rsaCipher: Cipher  = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    val ppkSpec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
    val privateKey = allDataService.privateKey()
    rsaCipher.init(Cipher.DECRYPT_MODE,privateKey, ppkSpec)
    return String(rsaCipher.doFinal(encryption), StandardCharsets.UTF_8)
}

private fun ByteArray.toBase64(): String =
    String(Base64.getEncoder().encode(this))

private fun String.fromBase64(): ByteArray =
    Base64.getDecoder().decode(this)

private fun ByteArray.toBase64Url(): String =
    Base64.getUrlEncoder().encodeToString(this)

private fun String.fromBase64Url(): ByteArray =
    Base64.getUrlDecoder().decode(this)

private fun sha256(str: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(str.toByteArray(Charsets.UTF_8))

val activeSessions = java.util.concurrent.ConcurrentHashMap<String, DefaultWebSocketServerSession>()

fun Route.websocketRoute() {
    val allDataService = AllDataService()
    
    webSocket("/wsData") {
        var clientHuid: String? = null
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText().trim()
                    
                    if (text.startsWith("[")) {
                        val messages: List<JsonAllData> = Json.decodeFromString(text)
                        for (msg in messages) {
                            if (msg.data == "SERVERKEY") {
                                val publicKeyString = allDataService.publicKey()
                                val publicKeyStringGzip = gzip(publicKeyString).toBase64Url()
                                send(Frame.Text(Json.encodeToString(listOf(JsonAllData(0L, "", "SERVERKEY$publicKeyStringGzip", false)))))
                            } else {
                                val unBuildPair = unbuildServerData(msg.serverData)
                                val newAllData = AllData(msg.timestamp, unBuildPair.first, msg.data, false)
                                allDataService.addOneAllData(newAllData)
                                
                                // Send ACK back to the sender
                                send(Frame.Text(Json.encodeToString(listOf(JsonAllData(msg.timestamp, unBuildPair.first, "ACK", false)))))
                                
                                 val recipientSession = activeSessions[unBuildPair.first]
                                 if (recipientSession != null) {
                                     try {
                                         recipientSession.send(Frame.Text(Json.encodeToString(listOf(msg))))
                                         if (storeMessage) {
                                             allDataService.updateMarkRead(listOf("'${unBuildPair.first}'"))
                                         } else {
                                             allDataService.deleteManyAllData(listOf("'${unBuildPair.first}'"))
                                         }
                                     } catch (e: Exception) {
                                         activeSessions.remove(unBuildPair.first)
                                     }
                                 }
                            }
                        }
                    } else {
                        try {
                            val unBuildPair = unbuildServerDataGet(text)
                            val huid = unBuildPair.first
                            clientHuid = huid
                            activeSessions[huid] = this
                            
                            val messagesRequested = allDataService.getAllData(huid, 100)
                            if (messagesRequested.isNotEmpty()) {
                                val jsonMessages = messagesRequested.map {
                                    JsonAllData(it.timestamp.toString().toLong(), "", it.data, it.readMessage)
                                }
                                send(Frame.Text(Json.encodeToString(jsonMessages)))
                                
                                val uidsFound = messagesRequested.map { "'${it.serverData}'" }
                                allDataService.deleteManyAllData(uidsFound)
                            }
                        } catch (e: Exception) {
                            send(Frame.Text(Json.encodeToString(listOf(JsonAllData(0L, "Auth/Decryption Failed: ${e.message}", "ERROR", false)))))
                        }
                    }
                }
            }
        } finally {
            clientHuid?.let {
                activeSessions.remove(it)
            }
        }
    }
}



