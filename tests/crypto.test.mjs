import test from "node:test";
import assert from "node:assert/strict";
import {
  generateKey,
  importKey,
  encryptMessage,
  decryptMessage,
  extractOtp,
  fromBase64,
  toBase64,
} from "../web/src/crypto.mjs";
test("encrypted messages round trip with Unicode and multipart content", async () => {
  const key = await importKey(generateKey());
  const payload = {
    sender: "Bank",
    body: "Your OTP is 123456. नमस्ते 👋",
    sim: "SIM 2",
  };
  const record = {
    id: "m",
    deviceId: "d",
    ...(await encryptMessage(key, payload, "u", "d", "m")),
  };
  assert.deepEqual(await decryptMessage(key, record, "u"), payload);
  assert.equal(record.ciphertext.includes("123456"), false);
});
test("wrong key, altered ciphertext and moved records are rejected", async () => {
  const key = await importKey(generateKey());
  const record = {
    id: "m",
    deviceId: "d",
    ...(await encryptMessage(key, { body: "Secret" }, "u", "d", "m")),
  };
  await assert.rejects(
    decryptMessage(await importKey(generateKey()), record, "u"),
  );
  await assert.rejects(decryptMessage(key, { ...record, id: "other" }, "u"));
  await assert.rejects(decryptMessage(key, record, "someone-else"));
  const bytes = fromBase64(record.ciphertext);
  bytes[0] ^= 1;
  await assert.rejects(
    decryptMessage(key, { ...record, ciphertext: toBase64(bytes) }, "u"),
  );
});
test("invalid keys fail and OTP extraction does not copy ordinary numbers", async () => {
  await assert.rejects(importKey("abcd"));
  assert.equal(extractOtp("Your verification code is 482913."), "482913");
  assert.equal(extractOtp("Flight 482913 is ready."), null);
});
test("OTP extraction picks the code near its keyword, not amounts or other numbers", async () => {
  const { readFile } = await import("node:fs/promises");
  const cases = JSON.parse(await readFile(new URL("./otp-cases.json", import.meta.url), "utf8"));
  for (const { body, otp } of cases) assert.equal(extractOtp(body), otp, body);
});
