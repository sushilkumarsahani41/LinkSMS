# Initial project status — 15 November 2026

## Ready locally

- React demo inbox served by `npm run dev`, with `?demo=1` for an explicit demo.
- Android debug APK builds and lint has no errors. Warnings remain for version updates and synchronous preference writes.
- Three encryption/OTP tests and three browser workflow tests pass, including account-form validation.
- Firestore authorization tests pass against a local demo emulator.
- Production npm dependency audit: zero reported vulnerabilities. Development tooling audit reports 14 moderate findings; review before upgrading/releasing.
- Source, lockfile, Firebase rules, setup guide and Git repository created.

## Firebase changes made

Project `linksms-9e899` already existed and was accessible through the signed-in Firebase CLI.

- Registered Android app `com.linksms.app`, app ID `1:514719083100:android:bd31e001c29bfebe951499`.
- Downloaded its config to ignored `android/app/google-services.json`.
- Registered web app `LinkSMS Web`, app ID `1:514719083100:web:f90be500a559aa61951499`.
- Web SDK configuration is saved in ignored `web/.env.local`. The local web app now supports live sign-in and account creation.
- With user authorization for live testing, enabled Firestore and created the default Standard database in Mumbai (`asia-south1`), confirmed free-tier eligible. Enabled email/password authentication through the Firebase CLI and deployed the tested Firestore rules.
- Production Firestore rules and authentication provider configuration are deployed. Hosting remains undeployed; the web app is served locally.

## Next device validation

The APK is installed on both the Nord 3 and POCO Pad; each activity launched successfully over ADB. The POCO Pad required enabling Install via USB; the Nord 3 initially rejected installation through app verification, then accepted a later user-requested retry. Verify exact Android versions and test an ordinary SMS and a real OTP on all three SIMs. Android 16 QPR2 adds OTP access restrictions that may require an eligible full default-SMS app; this MVP does not implement that role. Do not assume OTP support from an ordinary SMS test.

Pairing currently uses a manually shared encryption key. There is no QR pairing, recovery, automatic retention, old-SMS import, full SMS composer, or independent device credential revocation.

## Live cloud verification

A temporary account successfully created a device document, uploaded an AES-GCM-encrypted synthetic message, received it through a realtime listener, and decrypted it correctly. No plaintext body/sender fields were stored. The first attempt immediately after deployment was denied; the later attempt passed without loosening rules. Temporary test documents and accounts were cleaned up. The user confirmed that a normal SMS sent to the first Nord 3 SIM appeared in the web inbox. Other SIMs, actual OTPs and offline/background reliability still need separate validation.

## Android inbox update (0.2.0)

Implemented a native unified inbox, source filters, search, copy actions, phone-screen-lock app protection, custom device/SIM names, optional carrier/slot discovery, and encrypted hardware-model metadata for new messages. The original saved key and account are preserved on upgrade. Passkeys remain out of scope; no billing plan changes.

The user confirmed that screen-lock authentication opens the POCO Pad inbox and displays synced messages. Android build/lint and all three Android unit tests pass, as do the web build and six web tests. The final update adds optional carrier/slot discovery. LinkSMS 0.2.0 was also installed successfully on the Nord 3 via an in-place update. Its main activity launched and SMS permission remained granted. Screen-lock authentication and message viewing on the Nord 3 await owner confirmation.
