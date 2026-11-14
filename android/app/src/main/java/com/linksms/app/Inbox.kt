package com.linksms.app

import java.util.Base64
import com.google.firebase.firestore.DocumentSnapshot
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class InboxMessage(
    val id: String, val deviceId: String, val sender: String, val body: String,
    val sim: String, val model: String, val receivedAt: Long, val unreadable: Boolean = false
)
data class LinkedDevice(val id: String, val name: String, val enabled: Boolean)

object MessageCodec {
    fun decrypt(key: SecretKey, uid: String, record: DocumentSnapshot): InboxMessage {
        val deviceId = record.getString("deviceId") ?: "unknown"
        val receivedAt = record.getLong("receivedAt") ?: 0L
        return try {
            require(record.getLong("version") == 1L)
            val payload = decryptPayload(key, uid, deviceId, record.id, record.getString("iv") ?: "", record.getString("ciphertext") ?: "")
            InboxMessage(record.id, deviceId, payload.optString("sender", "Unknown sender"), payload.optString("body"), payload.optString("sim", "Unknown SIM"), payload.optString("model"), receivedAt)
        } catch (_: Exception) {
            InboxMessage(record.id, deviceId, "Cannot decrypt message", "This message uses a different encryption key, or its data is invalid.", "Unknown SIM", "", receivedAt, true)
        }
    }
    fun decryptPayload(key: SecretKey, uid: String, deviceId: String, id: String, iv: String, ciphertext: String): JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
        cipher.updateAAD("$uid/$deviceId/$id".toByteArray(Charsets.UTF_8))
        return JSONObject(String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), Charsets.UTF_8))
    }
    fun otp(body: String): String? = if (Regex("otp|code|verification|one.time|password", RegexOption.IGNORE_CASE).containsMatchIn(body)) Regex("\\b\\d{4,8}\\b").find(body)?.value else null
}
