# LinkSMS project conventions

- This is a private receive-only Android + Firebase + React project for the owner's Nord 3 and POCO Pad.
- Keep message bodies, sender addresses, SIM labels, OTPs and encryption keys out of plaintext cloud writes and logs.
- Preserve explicit opt-in, pause controls, account isolation and fail-closed Firestore rules.
- Never commit `.env.local`, `google-services.json`, credentials, signing keys or generated build artifacts.
- Test encryption interoperability and authorization rules when changing either contract.
- Do not publish/deploy the web app or change production rules without a user request authorizing deployment.
- Do not describe emulator/build checks as real-device delivery verification.
