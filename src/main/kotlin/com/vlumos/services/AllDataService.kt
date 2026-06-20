package com.vlumos.services

import com.vlumos.data.model.AllData
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class AllDataService {
    companion object {
        private val connectionRead = System.getenv("connectionRead") ?: "mongodb://localhost:27017"
        private val clientRead = KMongo.createClient(connectionRead).coroutine
        private val databaseRead = clientRead.getDatabase("Vlumos")
        private val collectionAllDataRead = databaseRead.getCollection<AllData>()

        private val connectionWrite = System.getenv("connectionWrite") ?: "mongodb://localhost:27017"
        private val clientWrite = KMongo.createClient(connectionWrite).coroutine
        private val databaseWrite = clientWrite.getDatabase("Vlumos")
        private val collectionAllDataWrite = databaseWrite.getCollection<AllData>()

        private val publicKeyString: String
        private val privateKey: PrivateKey

        init {
            val envPublicKey = System.getenv("publicKeyString")
            val envPrivateKey = System.getenv("privateKeyString")

            val cleanPublic = envPublicKey?.let { cleanKeyString(it, "PUBLIC_KEY=") }
            val cleanPrivate = envPrivateKey?.let { cleanKeyString(it, "PRIVATE_KEY=") }

            var parsedPrivateKey: PrivateKey? = null
            if (cleanPrivate != null && cleanPrivate.startsWith("MII")) {
                try {
                    parsedPrivateKey = stringToPrivateKey(cleanPrivate)
                } catch (e: Exception) {
                    println("[Vlumos-Server] WARNING: Failed to parse environment privateKeyString (${e.message}).")
                }
            }

            val hasValidPublic = cleanPublic != null && cleanPublic.startsWith("MII")
            val hasValidPrivate = parsedPrivateKey != null

            if (hasValidPublic && hasValidPrivate) {
                publicKeyString = cleanPublic
                privateKey = parsedPrivateKey
            } else {
                val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
                keyPairGenerator.initialize(2048)
                val keyPair = keyPairGenerator.generateKeyPair()

                val generatedPrivate = keyPair.private
                val generatedPublic = keyPair.public

                val genPrivateStr = Base64.getEncoder().encodeToString(generatedPrivate.encoded)
                val genPublicStr = Base64.getEncoder().encodeToString(generatedPublic.encoded)

                publicKeyString = genPublicStr
                privateKey = generatedPrivate

                println("========================================================================")
                println("[Vlumos-Server] ERROR: Environment keys are missing or invalid!")
                println("[Vlumos-Server] Generated a temporary RSA key pair for this session.")
                println("[Vlumos-Server] Please add the following to your environment variables:")
                println("------------------------------------------------------------------------")
                println("publicKeyString=PUBLIC_KEY=$genPublicStr")
                println("------------------------------------------------------------------------")
                println("privateKeyString=PRIVATE_KEY=$genPrivateStr")
                println("========================================================================")
                
                System.exit(0)
            }
        }

        private fun cleanKeyString(key: String, prefixToRemove: String): String {
            var clean = key.trim()
            if (clean.startsWith(prefixToRemove)) {
                clean = clean.substring(prefixToRemove.length).trim()
            }
            return clean
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("-", "+")
                .replace("_", "/")
        }

        private fun stringToPrivateKey(privateKeyString: String): PrivateKey {
            val cleanString = cleanKeyString(privateKeyString, "PRIVATE_KEY=")
            val byteArrayPrivateKey: ByteArray = cleanString.fromBase64()
            val pkcs8eSpec = PKCS8EncodedKeySpec(byteArrayPrivateKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(pkcs8eSpec)
        }

        private fun String.fromBase64(): ByteArray =
            Base64.getDecoder().decode(this)
    }

    fun publicKey(): String = publicKeyString
    fun privateKey(): PrivateKey = privateKey

    suspend fun addOneAllData(allData: AllData) {
        collectionAllDataWrite.insertOne(allData)
    }

    suspend fun getAllData(huid: String, amount: Int): List<AllData> {
        return collectionAllDataRead.find(
            AllData::serverData eq huid
        )
            .limit(amount).toList()
    }

    suspend fun getAllUnreadData(huid: String, amount: Int): List<AllData> {
        return collectionAllDataRead.find(
            AllData::serverData eq huid, AllData::readMessage eq false)
            .limit(amount).toList()
    }

    suspend fun deleteManyAllData(uidsToDelete: List<String>) {
        val deleteQuery = "{ serverData: { \$in: $uidsToDelete } }"
        collectionAllDataWrite.deleteMany(deleteQuery)
    }

    suspend fun updateMarkRead(uidsToMarkRead: List<String>) {
        collectionAllDataWrite.updateMany(
            "{ serverData: { \$in: $uidsToMarkRead } }", "{ \$set:{ readMessage: true}}")
    }

    private fun stringToPrivateKey(privateKeyString: String): PrivateKey {
        val cleanString = cleanKeyString(privateKeyString, "PRIVATE_KEY=")
        val byteArrayPrivateKey: ByteArray = cleanString.fromBase64()
        val pkcs8eSpec = PKCS8EncodedKeySpec(byteArrayPrivateKey)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(pkcs8eSpec)
    }

    private fun String.fromBase64(): ByteArray =
        Base64.getDecoder().decode(this)
}