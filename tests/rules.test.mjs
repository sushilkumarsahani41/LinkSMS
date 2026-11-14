import test, { before, after } from "node:test";
import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from "@firebase/rules-unit-testing";
import {
  doc,
  setDoc,
  getDoc,
  updateDoc,
  serverTimestamp,
} from "firebase/firestore";
let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-link-sms",
    firestore: {
      rules: readFileSync("firebase/firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8189,
    },
  });
});
after(async () => {
  await env?.cleanup();
});
test("SMS contents are isolated; disabled devices and plaintext uploads are denied", async () => {
  const owner = env.authenticatedContext("alice").firestore();
  const device = doc(owner, "users/alice/devices/phone");
  await assertSucceeds(
    setDoc(device, {
      name: "Nord",
      enabled: true,
      lastSeen: serverTimestamp(),
    }),
  );
  const record = {
    deviceId: "phone",
    iv: "abcdefghijklmnop",
    ciphertext: "encrypted",
    version: 1,
    receivedAt: 1,
    uploadedAt: serverTimestamp(),
  };
  await assertSucceeds(
    setDoc(doc(owner, "users/alice/messages/first"), record),
  );
  await assertSucceeds(getDoc(doc(owner, "users/alice/messages/first")));
  await assertFails(
    getDoc(
      doc(
        env.authenticatedContext("bob").firestore(),
        "users/alice/messages/first",
      ),
    ),
  );
  await assertFails(
    getDoc(
      doc(
        env.unauthenticatedContext().firestore(),
        "users/alice/messages/first",
      ),
    ),
  );
  await assertFails(
    setDoc(doc(owner, "users/alice/messages/plaintext"), {
      ...record,
      body: "secret otp",
    }),
  );
  await assertFails(
    updateDoc(doc(owner, "users/alice/messages/first"), {
      ciphertext: "overwritten",
    }),
  );
  await assertSucceeds(updateDoc(device, { enabled: false }));
  await assertFails(setDoc(doc(owner, "users/alice/messages/paused"), record));
  await assertFails(
    setDoc(doc(owner, "users/bob/devices/intruder"), {
      name: "x",
      enabled: true,
      lastSeen: serverTimestamp(),
    }),
  );
});
