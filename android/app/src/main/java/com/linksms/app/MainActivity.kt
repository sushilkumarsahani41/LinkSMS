package com.linksms.app

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.tasks.await

class MainActivity : FragmentActivity() {
    private var permissionGranted by mutableStateOf(false)
    private var simPermissionGranted by mutableStateOf(false)
    private var unlocked by mutableStateOf(false)
    private var lockError by mutableStateOf("")
    private val requestSms = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    private val requestSimDetails = registerForActivityResult(ActivityResultContracts.RequestPermission()) { simPermissionGranted = it }
    private val deviceCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        unlocked = it.resultCode == RESULT_OK
        if (!unlocked) lockError = "Unlock cancelled. Your messages remain locked."
    }
    override fun onResume() {
        super.onResume()
        permissionGranted = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        simPermissionGranted = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    override fun onStop() {
        if (Settings(this).lockEnabled) unlocked = false
        super.onStop()
    }
    private fun unlockInbox() {
        lockError = ""
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            lockError = "Set a screen-lock PIN, password or pattern in your phone settings first."
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; lockError = "" }
                override fun onAuthenticationError(code: Int, message: CharSequence) { lockError = message.toString() }
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock LinkSMS").setSubtitle("Use your phone's screen lock to view messages").setAllowedAuthenticators(authenticators).build())
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent("Unlock LinkSMS", "Confirm your phone's screen lock")
            if (intent != null) deviceCredential.launch(intent)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val settings = Settings(this)
        val configured = FirebaseApp.getApps(this).isNotEmpty()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF236957), background = Color(0xFFF5F7F3))) {
                var signedIn by remember { mutableStateOf(configured && FirebaseAuth.getInstance().currentUser != null) }
                var lockEnabled by remember { mutableStateOf(settings.lockEnabled) }
                var page by remember { mutableStateOf("Inbox") }
                var keyReady by remember { mutableStateOf(settings.hasKey()) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("LinkSMS", style = MaterialTheme.typography.headlineMedium); Text("Your messages, together.", style = MaterialTheme.typography.bodySmall) }
                            if (signedIn && unlocked && lockEnabled) TextButton(onClick = { unlocked = false }) { Text("Lock") }
                        }
                        when {
                            !configured -> Text("Firebase setup needed. Add google-services.json and rebuild this app.")
                            !signedIn -> LoginPanel { signedIn = true; unlocked = false }
                            lockEnabled && !unlocked -> Column(Modifier.padding(top = 60.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("Your inbox is locked", style = MaterialTheme.typography.headlineSmall)
                                Text("Unlock with fingerprint, supported face recognition, or your device PIN. Background SMS sync continues while locked.")
                                Button(onClick = { unlockInbox() }) { Text("Unlock with screen lock") }
                                if (lockError.isNotBlank()) Text(lockError, color = MaterialTheme.colorScheme.error)
                            }
                            else -> {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilterChip(selected = page == "Inbox", onClick = { page = "Inbox" }, label = { Text("Inbox") })
                                    FilterChip(selected = page == "Settings", onClick = { page = "Settings" }, label = { Text("Devices & settings") })
                                }
                                if (page == "Inbox" && keyReady) UnifiedInbox(settings)
                                else if (page == "Inbox") Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Connect this device once", style = MaterialTheme.typography.titleLarge)
                                    Text("Add the same encryption key you use in your web inbox. LinkSMS saves it securely on this device; after that, use your screen lock to open the inbox.")
                                    Button(onClick = { page = "Settings" }) { Text("Set up this device") }
                                }
                                else SettingsPanel(settings, permissionGranted, simPermissionGranted, lockEnabled,
                                    onSimPermission = { requestSimDetails.launch(Manifest.permission.READ_PHONE_STATE) },
                                    onSmsPermission = { requestSms.launch(Manifest.permission.RECEIVE_SMS) },
                                    onKeySaved = { keyReady = settings.hasKey() },
                                    onLockChanged = { lockEnabled = it; settings.lockEnabled = it; unlocked = true },
                                    onSignOut = { settings.enabled = false; settings.clearKey(); Queue(this@MainActivity).use { it.clear() }; FirebaseAuth.getInstance().signOut(); signedIn = false; unlocked = false; keyReady = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginPanel(onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Text("Use the account you created in your LinkSMS web inbox.")
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && email.isNotBlank() && password.isNotBlank(), onClick = { scope.launch {
            busy = true
            try { FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await(); password = ""; onSignedIn() }
            catch (_: Exception) { error = "Sign-in failed. Check your account, password and connection." }
            finally { busy = false }
        } }) { Text(if (busy) "Signing in…" else "Sign in") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun UnifiedInbox(settings: Settings) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    val key = remember { runCatching { settings.key() }.getOrNull() }
    var messages by remember { mutableStateOf(emptyList<InboxMessage>()) }
    var devices by remember { mutableStateOf(emptyList<LinkedDevice>()) }
    var filter by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var fromCache by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    DisposableEffect(uid, key) {
        val deviceListener = db.collection("users/$uid/devices").addSnapshotListener { snap, failure ->
            if (failure != null) error = "Could not load device names. Check your connection."
            else devices = snap?.documents.orEmpty().map { LinkedDevice(it.id, it.getString("name") ?: "Unnamed device", it.getBoolean("enabled") == true) }
        }
        val messageListener = db.collection("users/$uid/messages").orderBy("receivedAt", Query.Direction.DESCENDING).limit(200).addSnapshotListener(MetadataChanges.INCLUDE) { snap, failure ->
            loading = false
            if (failure != null) error = "Could not load messages. Check your connection and account."
            else if (key != null && snap != null) {
                error = ""
                fromCache = snap.metadata.isFromCache
                messages = snap.documents.map { MessageCodec.decrypt(key, uid, it) }
            }
        }
        onDispose { deviceListener.remove(); messageListener.remove() }
    }
    val visible = messages.filter { (filter.isBlank() || it.deviceId == filter) && "${it.sender} ${it.body} ${it.sim}".contains(search, ignoreCase = true) }
    Column {
        OutlinedTextField(search, { search = it }, label = { Text("Search messages") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter.isBlank(), onClick = { filter = "" }, label = { Text("All devices") })
            devices.forEach { d -> FilterChip(selected = filter == d.id, onClick = { filter = d.id }, label = { Text(d.name) }) }
        }
        if (fromCache) Text("Showing saved messages; waiting for cloud connection.", style = MaterialTheme.typography.bodySmall)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (key == null) Text("The saved key could not be opened. Reconnect this device in settings.")
        if (copied) Text("Copied. Clear your clipboard when finished.", style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (visible.isEmpty()) Text(if (search.isNotBlank()) "No matching messages." else "No messages yet. New SMS from your linked devices will appear here.", modifier = Modifier.padding(vertical = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message.sender, style = MaterialTheme.typography.titleMedium)
                        Text("${devices.find { it.id == message.deviceId }?.name ?: "Unknown device"} · ${message.sim}", style = MaterialTheme.typography.labelMedium)
                        if (message.model.isNotBlank()) Text(message.model, style = MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(message.receivedAt)), style = MaterialTheme.typography.bodySmall)
                        Text(message.body)
                        if (!message.unreadable) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MessageCodec.otp(message.body)?.let { code -> TextButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }) { Text("Copy code") } }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(message.body)); copied = true }) { Text("Copy message") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(settings: Settings, smsPermission: Boolean, simPermission: Boolean, lockEnabled: Boolean, onSimPermission: () -> Unit, onSmsPermission: () -> Unit, onKeySaved: () -> Unit, onLockChanged: (Boolean) -> Unit, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(settings.name) }
    var sim1 by remember { mutableStateOf(settings.simName(1)) }
    var sim2 by remember { mutableStateOf(settings.simName(2)) }
    var keyText by remember { mutableStateOf("") }
    var keyReady by remember { mutableStateOf(settings.hasKey()) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var status by remember { mutableStateOf(settings.status) }
    var busy by remember { mutableStateOf(false) }
    var changingKey by remember { mutableStateOf(false) }
    var signOutConfirmation by remember { mutableStateOf(false) }
    if (signOutConfirmation) AlertDialog(onDismissRequest = { signOutConfirmation = false }, title = { Text("Sign out?") }, text = { Text("This clears the saved key and any unsent messages on this device. Cloud messages and original SMS stay in place. Keep your recovery key before signing out.") }, confirmButton = { TextButton(onClick = onSignOut) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { signOutConfirmation = false }) { Text("Cancel") } })
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("This device", style = MaterialTheme.typography.titleLarge)
        Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Device nickname") }, placeholder = { Text("Travel phone / Home tablet") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sim1, { sim1 = it.take(60) }, label = { Text("SIM 1 nickname") }, placeholder = { Text("Personal · Airtel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sim2, { sim2 = it.take(60) }, label = { Text("SIM 2 nickname (if present)") }, placeholder = { Text("Work · Jio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (simPermission) {
            val activeSims = remember(simPermission) { runCatching { if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty() else emptyList() }.getOrDefault(emptyList()) }
            if (activeSims.isEmpty()) Text("No active SIM details reported by Android.", style = MaterialTheme.typography.bodySmall)
            activeSims.forEach { sim -> Text("SIM ${sim.simSlotIndex + 1} · ${sim.carrierName} · Subscription ${sim.subscriptionId}", style = MaterialTheme.typography.bodySmall) }
        } else {
            OutlinedButton(onClick = onSimPermission) { Text("Read SIM details (optional)") }
            Text("Optional phone permission shows carriers and SIM slots. Phone numbers are not read or uploaded.", style = MaterialTheme.typography.bodySmall)
        }
        Text("SIM labels apply to new messages when Android supplies the SIM slot. Otherwise the subscription ID is shown. Changing a nickname does not rewrite older encrypted messages.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy && name.isNotBlank(), onClick = { scope.launch {
            busy = true
            try {
                settings.setSimName(1, sim1); settings.setSimName(2, sim2)
                val uid = FirebaseAuth.getInstance().currentUser!!.uid
                val doc = FirebaseFirestore.getInstance().document("users/$uid/devices/${settings.deviceId}")
                if (doc.get().await().exists()) doc.update("name", name.trim()).await()
                settings.name = name.trim(); status = "Device and SIM names saved."
            } catch (_: Exception) { status = "Could not save the device name. Check your connection." }
            finally { busy = false }
        } }) { Text("Save names") }
        HorizontalDivider()
        Text("App lock", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Use phone screen lock"); Switch(checked = lockEnabled, onCheckedChange = onLockChanged) }
        Text("When enabled, the inbox locks whenever you leave LinkSMS. SMS receiving and uploads continue in the background. Screenshots and recent-app previews are protected.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Connection & sync", style = MaterialTheme.typography.titleLarge)
        if (keyReady && !changingKey) {
            Text("Encryption key saved on this device. You do not need to enter it again to view messages.")
            TextButton(onClick = { changingKey = true }) { Text("Replace connection key") }
        } else {
            Text("Paste the same key used by your other devices once. Replacing it will not decrypt messages saved under a different key.")
            OutlinedTextField(keyText, { keyText = it }, label = { Text("Connection key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = keyText.isNotBlank(), onClick = {
                try { settings.saveKey(keyText); keyText = ""; keyReady = true; changingKey = false; onKeySaved(); status = "Connection key saved." }
                catch (_: Exception) { status = "Invalid key. Paste the complete key from your web inbox." }
            }) { Text("Save connection key") }
        }
        Text(if (smsPermission) "SMS permission granted" else "SMS permission is needed to sync messages received by this device.")
        if (!smsPermission) Button(onClick = onSmsPermission) { Text("Allow SMS access") }
        Button(enabled = !busy && smsPermission && keyReady && name.isNotBlank(), onClick = { scope.launch {
            busy = true
            try {
                val uid = FirebaseAuth.getInstance().currentUser!!.uid
                settings.name = name.trim(); settings.setSimName(1, sim1); settings.setSimName(2, sim2)
                FirebaseFirestore.getInstance().document("users/$uid/devices/${settings.deviceId}").set(mapOf("name" to settings.name, "enabled" to true, "lastSeen" to FieldValue.serverTimestamp())).await()
                settings.enabled = true; enabled = true; SyncWorker.enqueue(context); status = "Sync enabled. New SMS will appear in all connected inboxes."
            } catch (_: Exception) { status = "Could not enable sync. Check your connection and account." }
            finally { busy = false }
        } }) { Text(if (enabled) "Sync now" else "Enable encrypted sync") }
        if (enabled) OutlinedButton(onClick = { settings.enabled = false; enabled = false; status = "Capture and uploads paused. Queued messages are kept." }) { Text("Pause sync") }
        OutlinedButton(onClick = { status = settings.status }) { Text("Refresh sync status") }
        Text(status)
        Text("Keep the device powered on with internet and cellular reception. Allow background activity and auto-start in battery settings. Offline messages retry when internet returns; Android may delay delivery.", style = MaterialTheme.typography.bodySmall)
        if (Build.VERSION.SDK_INT >= 36) Text("Android 16 can restrict OTP SMS access. Test actual OTP delivery separately from ordinary SMS.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { signOutConfirmation = true }) { Text("Sign out") }
    }
}
