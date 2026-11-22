# LinkSMS

A private, receive-only SMS inbox for a OnePlus Nord 3 and POCO Pad, with a web inbox for Mac and PC. Firebase project: `linksms-9e899`.

## What is implemented

- Kotlin / Jetpack Compose Android app: combined inbox across all linked devices, source filters, search, OTP/message copy, screen-lock authentication, email/password sign-in, SMS permission, opt-in sync, device/SIM naming, pause and sign-out.
- New incoming SMS capture, multipart assembly, best-effort SIM slot/subscription labels, AES-256-GCM encryption before writing to a durable SQLite outbox, and WorkManager network retry.
- React web inbox: demo mode, sign-in, latest 200 messages, device and verification-code filters, search, copy code/message, delete synced messages, and remote pause/resume of uploads.
- Firestore rules isolate each user's documents and reject plaintext fields and uploads from paused devices.
- Firebase Hosting configuration, encryption tests and Firestore rule tests.

This is a development MVP. Real SIM delivery and background reliability must be checked on both devices before relying on it for travel OTPs. It does not send SMS, import existing history, capture RCS/MMS, or offer guaranteed instant delivery. It is not prepared for Play Store submission.

## Run the web app

Use Node.js 22.12+ (Node 24 recommended):

```sh
npm install
npm run dev
```

Open the local URL printed by Vite. Add `?demo=1` to view sample messages even when live configuration is present. Without Firebase web configuration, the app opens a clearly labelled demo. To connect live data, create `web/.env.local` using `web/.env.example`, or use the Firebase CLI to obtain the registered web client's SDK config. Restart Vite after changing environment variables. Firebase client API keys identify the project; authorization is enforced by Authentication and Firestore rules. Never put service account keys in the web app.

## Firebase setup

1. Open [Firebase project settings](https://console.firebase.google.com/project/linksms-9e899/settings/general). The Android package is `com.linksms.app`.
2. Download the Android client's `google-services.json` into `android/app/`. Add the web client's API key, app ID and auth domain to `web/.env.local`. These local files are ignored by Git.
3. In Authentication → Sign-in method, enable Email/Password. Create your account using the web app’s **New here? Create account** button, or through the Firebase Users console. Use the same account on the web and both Android devices. Do not send your password in chat.
4. Create the default Cloud Firestore database if one does not exist. Choose an appropriate region and production mode.
5. Review and deploy the rules before enabling sync:

```sh
firebase login
firebase deploy --only firestore:rules --project linksms-9e899
```

6. Build the web app with `npm run build`. When ready to publish it:

```sh
firebase deploy --only hosting --project linksms-9e899
```

No Functions or SMS-based Firebase Authentication are required. The UI can be publicly reachable after hosting deployment, but inbox data requires your account and your encryption key. Hosting deployment is not part of the initial local setup.

## Build Android

On this Mac, `./scripts/build-android.sh` uses the project-local JDK and Gradle downloaded during setup. On another machine, install JDK 17 or 21 and Android SDK platform 35/build tools 35. Set `ANDROID_HOME` or `android/local.properties` (`sdk.dir=...`).

```sh
cd android
./gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. The Gradle wrapper includes a pinned distribution checksum. If `google-services.json` is absent, the app still builds and shows a setup screen without collecting SMS.

### Signed release build

`./scripts/build-android-release.sh` runs release unit tests and lint, then builds a minified, signed APK at `android/app/build/outputs/apk/release/app-release.apk`. Signing details are read from `~/.linksms-signing/keystore.properties` (or the path in `LINKSMS_SIGNING_PROPERTIES`):

```properties
storeFile=/absolute/path/to/linksms-release.jks
storePassword=...
keyAlias=linksms
keyPassword=...
```

Keep the keystore and its properties out of the repository and back them up. Losing the keystore means future versions cannot update an installed copy. Moving an installed debug build to a release build requires uninstalling first, which clears that device's saved key and unsent queue.

For a private development installation, enable USB debugging and use Android SDK platform-tools:

```sh
adb devices
adb -s DEVICE_SERIAL install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Open the app and explicitly allow SMS access. `RECEIVE_SMS` is a hard-restricted Android permission: whether it can be granted depends on the installer and OS restrictions. Do not assume every sideload path will grant it; verify the permission on the actual devices. Public Play Store distribution requires a separate policy review and eligible use case.

**OTP compatibility:** Android 16 QPR2 (API 36.1) introduces additional restrictions on OTP-containing SMS via `RECEIVE_SENSITIVE_NOTIFICATIONS`. A normal SMS permission grant may not be enough. This MVP is not a full default-SMS app and does not request a privileged permission it cannot obtain. Verify the exact OS build and real OTP delivery; supporting restricted builds may require implementing an eligible full default-SMS role. See the [Android permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SENSITIVE_NOTIFICATIONS).

## Pair and use

1. Sign in to the web app. Open **Settings & setup** and generate an encryption key.
2. Copy the key into your password manager before unlocking the inbox. Losing it makes previously encrypted messages unreadable.
3. Sign in on both Android devices using the same account, paste the same key, name each device, grant SMS access, and enable encrypted sync.
4. Keep both devices powered on with cellular reception and internet. Allow LinkSMS background activity and auto-start in each device's battery settings. Do not force-stop it.
5. Send a fresh SMS to each SIM and check the device and SIM label in the web inbox. Some firmware supplies only a subscription ID; SIM labels are best effort in this MVP.
6. Refreshing the web app locks it. Paste your saved key to unlock again. Authentication is browser-session scoped.

Remote pause prevents new uploads but does not remove the account or key from the phone. The phone may continue queuing encrypted messages. Resume permits queued messages to upload. Local pause stops both capture and upload; messages arriving while locally paused are not backfilled. Signing out locally erases that device's queued messages and encryption key. It does not delete the original SMS or cloud history.

## Encryption and limits

Message bodies, senders and SIM labels are encrypted with a random 256-bit shared key and a fresh 96-bit GCM nonce per message. Authenticated additional data binds ciphertext to `uid/deviceId/messageId`. Android wraps the shared key using Android Keystore. The web key remains in memory only; ciphertext is decrypted in the browser. The cloud still sees user/device identifiers, device names, timestamps, sizes and traffic patterns.

Both phones and each unlocked browser share the key, so this first version does not provide cryptographic per-device revocation, key recovery or automatic key rotation. A compromised signed-in client can affect its owner's data. Remote pause is a convenience control, not a security boundary against that client. Lost-device handling needs account-session revocation and key rotation; old ciphertext remains protected only by the old key. Do not expose the key or OTPs in logs or analytics.

Only the latest 200 messages are displayed; there is no pagination or automatic retention cleanup yet. OTP copy is a heuristic (4–8 digits in messages mentioning a code/OTP); check the full text when ambiguous. Delivery can be delayed by Android power management or loss of internet, and an OTP may expire before it syncs. Original SMS and messages arriving before opt-in are untouched.

## Checks

```sh
npm test
npm run build
npm run test:rules
```

With the web dev server running, `npm run test:ui` runs the browser checks in installed Google Chrome.

Rule tests use the local Firestore emulator with a `demo-` project and do not access production. Java 21 is recommended for emulator tests. Run Android's `assembleDebug` and `lintDebug` with a supported JDK.

Device acceptance checklist:

- Receive one SMS on each of all three SIMs and check correct source labels.
- Receive a multipart message and a Unicode message.
- Leave a phone offline, receive an SMS, restore internet and verify a single upload.
- Test after screen-off, reboot and battery saver; inspect last contact time.
- Test remote pause/resume, local pause, sign-out and sign-in.
- Confirm another account cannot read your messages and a wrong encryption key cannot decrypt them.

Reference: [Android SMS broadcasts](https://developer.android.com/reference/android/provider/Telephony.Sms.Intents), [SMS permission restrictions](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS), [Firebase Android setup](https://firebase.google.com/docs/android/setup), [Play SMS policy](https://support.google.com/googleplay/android-developer/answer/10208820).

## Android 0.2: travel inbox and app lock

The Android app now reads the same latest-200-message cloud inbox as the web app. You can carry the Nord 3 and view SMS uploaded by the home tablet, provided the tablet remains online and enabled for sync. The inbox works independently of whether the viewing device has local SMS permission.

- After one-time connection-key setup, Android saves the key using the existing Keystore wrapper. Opening the inbox uses the phone’s fingerprint, supported strong face biometric, or device PIN/password/pattern. The app locks when it leaves the foreground; a Lock button is also available. The lock can be disabled from unlocked settings.
- The app lock protects access through the UI. The background receiver still needs access to its saved encryption key to encrypt incoming SMS while locked. This is not a per-operation biometric-bound encryption key. Screenshots and recent-app previews are protected with FLAG_SECURE.
- Settings → Devices & settings allows a device nickname and SIM 1/SIM 2 nicknames. Device names update existing inbox source labels. SIM nicknames and hardware model are encrypted inside new messages, so older messages keep their original SIM labels.
- Optional Read SIM details permission shows carrier, slot and subscription ID and improves slot identification. Phone numbers are not read or uploaded. With no permission, Android broadcast metadata is used; subscription ID is the fallback when a slot is unavailable.
- New SMS uploads request expedited work on Android 12+, falling back to normal queued work when quota is exhausted. This improves scheduling but cannot guarantee instantaneous delivery or bypass Android power restrictions.
- Passkeys were explicitly removed from scope. Email/password sign-in remains. There are no paid backends, billing-plan changes or new Firestore schema/rule requirements. Browser unlocking continues to use the existing session key flow.

Validation includes Android build/lint, three Android unit tests (including a WebCrypto-produced ciphertext fixture), web build, encryption tests and browser workflow tests. Real fingerprint/PIN unlock must be confirmed by the device owner.

## Android 0.3: refreshed interface and automatic unlock

The Android interface now uses light/dark themes, rounded source-labelled message cards, a prominent OTP copy area, a codes-only filter, bottom navigation and grouped settings. With app lock enabled, opening or returning to LinkSMS automatically requests system authentication. Cancelling leaves the app locked with an explicit retry button; it does not repeatedly reopen the prompt. The manual Lock action also stays locked until you choose to unlock. Background capture and encryption are unchanged.

Four additional unit tests cover automatic-prompt suppression after cancellation, return-to-app behavior, credential-dialog transitions and manual locking. Device-owner confirmation is still required for successful biometric/PIN authentication.
