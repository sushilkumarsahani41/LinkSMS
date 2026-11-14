package com.linksms.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
 private var permissionGranted by mutableStateOf(false)
 private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
 override fun onResume() { super.onResume(); permissionGranted = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val settings = Settings(this)
  val configured = FirebaseApp.getApps(this).isNotEmpty()
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF236957), background = Color(0xFFF5F7F3))) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(settings.name) }
    var signedIn by remember { mutableStateOf(configured && FirebaseAuth.getInstance().currentUser != null) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var status by remember { mutableStateOf(settings.status) }
    var busy by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text("LinkSMS", style = MaterialTheme.typography.headlineLarge)
      Text("Your messages, together.", style = MaterialTheme.typography.titleMedium)
      Text("Private companion · ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
      if(android.os.Build.VERSION.SDK_INT >= 36) Text("OTP compatibility: newer Android 16 builds can restrict OTP messages to eligible messaging apps. Test real OTP delivery before relying on LinkSMS. This preview is not a default SMS app.")
      if(!configured) {
       Text("Firebase setup needed", style = MaterialTheme.typography.titleLarge)
       Text("Add your project's google-services.json to android/app, then rebuild this app. No messages are collected in this setup mode.")
      } else if(!signedIn) {
       Text("Sign in with the same account as your web inbox. Create this account in Firebase Authentication first.")
       OutlinedTextField(email, {email=it}, label={Text("Email")}, singleLine=true, modifier=Modifier.fillMaxWidth())
       OutlinedTextField(password, {password=it}, label={Text("Password")}, visualTransformation=PasswordVisualTransformation(), singleLine=true, modifier=Modifier.fillMaxWidth())
       Button(enabled=!busy && email.isNotBlank() && password.isNotBlank(), onClick={ scope.launch { busy=true; try { FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await(); signedIn=true; password=""; status="Signed in. Set up encryption below." } catch(_:Exception) { status="Sign-in failed. Check your account, password and connection." }; busy=false } }) { Text("Sign in") }
      } else {
       OutlinedTextField(name, {name=it.take(80)}, label={Text("Device name")}, modifier=Modifier.fillMaxWidth())
       Text("Paste the encryption key generated in your web inbox. Use the same key on both Android devices. Store a recovery copy safely.")
       OutlinedTextField(keyText, {keyText=it}, label={Text("Encryption key")}, visualTransformation=PasswordVisualTransformation(), singleLine=true, enabled=!enabled, modifier=Modifier.fillMaxWidth())
       Text(if(permissionGranted) "SMS permission granted" else "Allow SMS access to sync new incoming messages. Existing SMS history is not imported.")
       if(!permissionGranted) Button(onClick={requestSms.launch(Manifest.permission.RECEIVE_SMS)}) { Text("Allow SMS access") }
       Button(enabled=!busy && permissionGranted && name.isNotBlank(), onClick={ scope.launch {
        busy=true
        try {
         if(keyText.isNotBlank()) settings.saveKey(keyText)
         require(settings.key() != null) { "Paste your encryption key first." }
         settings.name=name
         val uid=FirebaseAuth.getInstance().currentUser!!.uid
         FirebaseFirestore.getInstance().document("users/$uid/devices/${settings.deviceId}").set(mapOf("name" to name.trim(), "enabled" to true, "lastSeen" to FieldValue.serverTimestamp())).await()
         settings.enabled=true; enabled=true; keyText=""; SyncWorker.enqueue(this@MainActivity); status="Sync enabled. Send a test SMS to this device."
        } catch(e:Exception) { status=if(e is IllegalArgumentException) e.message ?: "Invalid key" else "Setup failed. Check Firebase configuration, rules and internet." }
        busy=false
       } }) { Text(if(enabled) "Save and sync now" else "Enable encrypted sync") }
       if(enabled) OutlinedButton(onClick={settings.enabled=false;enabled=false;status="Sync paused on this device. Queued messages are kept."}) { Text("Pause sync") }
       OutlinedButton(onClick={status=settings.status}) { Text("Refresh sync status") }
       Text("For reliable delivery, allow background activity and auto-start for LinkSMS in your phone's battery settings. Keep this device powered on and connected to internet.")
       OutlinedButton(onClick={settings.enabled=false;enabled=false;settings.clearKey();Queue(this@MainActivity).use{it.clear()};FirebaseAuth.getInstance().signOut();signedIn=false;status="Signed out. Local encryption key and queued messages cleared."}) { Text("Sign out and clear local queue") }
      }
      HorizontalDivider()
      Text(status, style=MaterialTheme.typography.bodyMedium)
      Text("Only new incoming SMS are synced after you enable access. Message content is encrypted before upload; device names and timestamps remain visible to Firebase.", style=MaterialTheme.typography.bodySmall)
     }
    }
   }
  }
 }
}
