package com.vlumos.services

import com.vlumos.data.model.AllData
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

class AllDataService {
    companion object {
        private val connectionRead = System.getenv("connectionRead")
        private val clientRead = KMongo.createClient(connectionRead).coroutine
        private val databaseRead = clientRead.getDatabase("Vlumos")
        private val collectionAllDataRead = databaseRead.getCollection<AllData>()

        private val connectionWrite = System.getenv("connectionWrite")
        private val clientWrite = KMongo.createClient(connectionWrite).coroutine
        private val databaseWrite = clientWrite.getDatabase("Vlumos")
        private val collectionAllDataWrite = databaseWrite.getCollection<AllData>()
    }

    private val publicKeyString = System.getenv("publicKeyString")
    private val privateKeyString = System.getenv("privateKeyString")
    private val privateKey = stringToPrivateKey(privateKeyString)

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
        val byteArrayPrivateKey: ByteArray = privateKeyString.fromBase64()
        val pkcs8eSpec = PKCS8EncodedKeySpec(byteArrayPrivateKey)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(pkcs8eSpec)
    }

    private fun String.fromBase64(): ByteArray =
        Base64.getDecoder().decode(this)
}