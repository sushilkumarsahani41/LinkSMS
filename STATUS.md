# Initial project status — 15 November 2026

## Ready locally

- React demo inbox served by `npm run dev`, with `?demo=1` for an explicit demo.
- Android debug APK builds and lint has no errors. Warnings remain for version updates and synchronous preference writes.
- Three encryption/OTP tests and two browser workflow tests pass.
- Firestore authorization tests pass against a local demo emulator.
- Production npm dependency audit: zero reported vulnerabilities. Development tooling audit reports 14 moderate findings; review before upgrading/releasing.
- Source, lockfile, Firebase rules, setup guide and Git repository created.

## Firebase changes made

Project `linksms-9e899` already existed and was accessible through the signed-in Firebase CLI.

- Registered Android app `com.linksms.app`, app ID `1:514719083100:android:bd31e001c29bfebe951499`.
- Downloaded its config to ignored `android/app/google-services.json`.
- Registered web app `LinkSMS Web`, app ID `1:514719083100:web:f90be500a559aa61951499`.
- Web SDK config fetch repeatedly failed at the CLI network request. `web/.env.local` has not been filled; copy web SDK settings from Firebase Console.
- Cloud Firestore API is disabled / has not been used in this project. Database creation, email/password authentication setup, account creation and production rules deployment remain.
- No hosting deployment or production security-rule changes were made.

## Next device validation

The Nord 3 and POCO Pad have not been connected or tested. Verify exact Android versions and test an ordinary SMS and a real OTP on all three SIMs. Android 16 QPR2 adds OTP access restrictions that may require an eligible full default-SMS app; this MVP does not implement that role. Do not assume OTP support from an ordinary SMS test.

Pairing currently uses a manually shared encryption key. There is no QR pairing, recovery, automatic retention, old-SMS import, full SMS composer, or independent device credential revocation.
