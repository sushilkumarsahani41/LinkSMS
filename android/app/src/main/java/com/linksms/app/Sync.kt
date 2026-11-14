package com.linksms.app

import android.app.Application
import android.content.*
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import android.provider.Telephony
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class LinkSmsApp : Application() {
 override fun onCreate() { super.onCreate(); FirebaseApp.initializeApp(this); SyncWorker.schedule(this) }
}

class Settings(context: Context) {
 private val prefs = context.getSharedPreferences("linksms", Context.MODE_PRIVATE)
 val deviceId: String get() = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).commit() }
 var name: String
  get() = prefs.getString("name", android.os.Build.MODEL)!!
  set(value) { prefs.edit().putString("name", value.take(80)).apply() }
 var enabled: Boolean
  get() = prefs.getBoolean("enabled", false)
  set(value) { prefs.edit().putBoolean("enabled", value).commit() }
 var status: String
  get() = prefs.getString("status", "No messages synced yet")!!
  set(value) { prefs.edit().putString("status", value).apply() }
 private fun wrappingKey(): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  return (store.getKey("linksms-local", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder("linksms-local", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun saveKey(text: String) {
  val raw = Base64.decode(text.trim(), Base64.DEFAULT)
  require(raw.size == 32) { "Encryption key must contain 32 bytes." }
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
  prefs.edit().putString("key", b64(cipher.doFinal(raw))).putString("keyIv", b64(cipher.iv)).commit()
 }
 fun key(): SecretKey? {
  val saved = prefs.getString("key", null) ?: return null
  val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, Base64.decode(prefs.getString("keyIv", ""), Base64.DEFAULT))) }
  return SecretKeySpec(cipher.doFinal(Base64.decode(saved, Base64.DEFAULT)), "AES")
 }
 fun clearKey() { prefs.edit().remove("key").remove("keyIv").commit() }
 companion object { fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP) }
}

class Queue(context: Context) : SQLiteOpenHelper(context, "outbox.db", null, 1) {
 override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE outbox (id TEXT PRIMARY KEY, uid TEXT NOT NULL, data TEXT NOT NULL)") }
 override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
 fun add(id: String, uid: String, data: JSONObject) { writableDatabase.insertOrThrow("outbox", null, ContentValues().apply { put("id", id); put("uid", uid); put("data", data.toString()) }) }
 fun batch(uid: String): List<Pair<String, JSONObject>> = readableDatabase.query("outbox", arrayOf("id","data"), "uid = ?", arrayOf(uid), null, null, "rowid ASC", "100").use { c -> buildList { while(c.moveToNext()) add(c.getString(0) to JSONObject(c.getString(1))) } }
 fun remove(id: String) { writableDatabase.delete("outbox", "id = ?", arrayOf(id)) }
 fun clear() { writableDatabase.delete("outbox", null, null) }
}

class SmsReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  if(intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || FirebaseApp.getApps(context).isEmpty()) return
  val settings = Settings(context)
  if(!settings.enabled) return
  val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
  try {
   val key = settings.key() ?: return
   val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
   if(parts.isEmpty()) return
   val id = UUID.randomUUID().toString()
   val subscription = intent.extras?.get("subscription")?.toString() ?: "unknown"
   val slot = intent.extras?.get("slot")?.toString() ?: intent.extras?.get("slotIndex")?.toString()
   val payload = JSONObject().put("sender", parts[0].displayOriginatingAddress ?: "Unknown sender")
    .put("body", parts.joinToString("") { it.displayMessageBody ?: "" })
    .put("sim", if(slot != null) "SIM slot ${slot.toIntOrNull()?.plus(1) ?: slot}" else "Subscription $subscription")
   val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key); updateAAD("$uid/${settings.deviceId}/$id".toByteArray(Charsets.UTF_8)) }
   val ciphertext = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
   val record = JSONObject().put("deviceId", settings.deviceId).put("iv", Settings.b64(cipher.iv)).put("ciphertext", Settings.b64(ciphertext)).put("version", 1).put("receivedAt", parts[0].timestampMillis)
   Queue(context).use { it.add(id, uid, record) }
   settings.status = "Message encrypted and queued"
   SyncWorker.enqueue(context)
  } catch (_: Exception) { settings.status = "Message could not be queued. Open LinkSMS to check setup." }
 }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
 override suspend fun doWork(): Result {
  if(FirebaseApp.getApps(applicationContext).isEmpty()) return Result.success()
  val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
  val settings = Settings(applicationContext)
  if(!settings.enabled) return Result.success()
  try {
   val db = FirebaseFirestore.getInstance()
   val device = db.document("users/$uid/devices/${settings.deviceId}")
   val state = device.get(com.google.firebase.firestore.Source.SERVER).await()
   if(state.getBoolean("enabled") != true) { settings.status = "Uploads paused in web inbox"; return Result.success() }
   device.update("lastSeen", FieldValue.serverTimestamp()).await()
   Queue(applicationContext).use { queue ->
    do {
     val batch = queue.batch(uid)
     for((id, json) in batch) {
      if(!settings.enabled || FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
      val doc = db.document("users/$uid/messages/$id")
      if(!doc.get(com.google.firebase.firestore.Source.SERVER).await().exists()) {
       doc.set(mapOf("deviceId" to json.getString("deviceId"), "iv" to json.getString("iv"), "ciphertext" to json.getString("ciphertext"), "version" to 1, "receivedAt" to json.getLong("receivedAt"), "uploadedAt" to FieldValue.serverTimestamp())).await()
      }
      queue.remove(id)
     }
    } while(batch.size == 100)
   }
   settings.status = "Last sync: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}"
   return Result.success()
  } catch (error: Exception) {
   if(error is kotlinx.coroutines.CancellationException) throw error
   settings.status = "Sync pending. Check internet, login and Firebase rules."
   return Result.retry()
  }
 }
 companion object {
  private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
  fun enqueue(context: Context) { WorkManager.getInstance(context).enqueueUniqueWork("sms-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()) }
  fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("sms-retry", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()) }
 }
}
