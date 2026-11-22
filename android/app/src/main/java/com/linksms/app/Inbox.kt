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
) {
    /** Computed once per message instead of on every recomposition. */
    val otp: String? = if (unreadable) null else MessageCodec.otp(body)
    val searchText: String = "$sender $body $sim"
}
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
    // Keep these patterns identical to extractOtp in web/src/crypto.mjs (tests/otp-cases.json covers both).
    private val otpKeyword = Regex("otp|code|verification|one.time|password", RegexOption.IGNORE_CASE)
    private val otpCandidate = Regex("(?<![\\p{L}\\p{N}_.,])(?:\\d{3}[- ]\\d{3}|\\d{4,8})(?![\\p{L}\\p{N}_]|[.,]\\d)")
    private val amountPrefix = Regex("(?:rs\\.?|inr|usd|eur|gbp|[₹$€£])\\s*$", RegexOption.IGNORE_CASE)
    /** Returns the 4–8 digit code nearest a code keyword, preferring one that follows it; skips amounts. */
    fun otp(body: String): String? {
        val keywords = otpKeyword.findAll(body).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (c in otpCandidate.findAll(body)) {
            val start = c.range.first
            if (amountPrefix.containsMatchIn(body.substring(maxOf(0, start - 5), start))) continue
            val end = c.range.last + 1
            for (k in keywords) {
                val kEnd = k.last + 1
                val score = if (start >= kEnd) start - kEnd else if (k.first >= end) (k.first - end) * 2 else continue
                if (score < bestScore) { best = c.value; bestScore = score }
            }
        }
        return best?.replace("-", "")?.replace(" ", "")
    }
}

/** Caches decrypted messages by id so each Firestore snapshot only decrypts records it has not seen. */
class DecryptCache(private val key: SecretKey, private val uid: String) {
    private val items = HashMap<String, Pair<String, InboxMessage>>()
    fun decryptAll(records: List<DocumentSnapshot>): List<InboxMessage> {
        val live = HashSet<String>(records.size)
        val result = records.map { record ->
            live += record.id
            val stamp = (record.getString("iv") ?: "") + (record.getString("ciphertext") ?: "")
            items[record.id]?.takeIf { it.first == stamp }?.second
                ?: MessageCodec.decrypt(key, uid, record).also { items[record.id] = stamp to it }
        }
        items.keys.retainAll(live)
        return result
    }
}
