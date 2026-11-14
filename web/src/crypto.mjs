const encoder = new TextEncoder();
export function toBase64(bytes) {
  return btoa(String.fromCharCode(...bytes));
}
export function fromBase64(value) {
  return Uint8Array.from(atob(value.trim()), (c) => c.charCodeAt(0));
}
export function generateKey() {
  return toBase64(crypto.getRandomValues(new Uint8Array(32)));
}
export async function importKey(value) {
  const bytes = fromBase64(value);
  if (bytes.length !== 32)
    throw new Error("Enter the complete 32-byte encryption key.");
  return crypto.subtle.importKey("raw", bytes, "AES-GCM", false, [
    "encrypt",
    "decrypt",
  ]);
}
export async function encryptMessage(key, payload, uid, deviceId, messageId) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    {
      name: "AES-GCM",
      iv,
      additionalData: encoder.encode(`${uid}/${deviceId}/${messageId}`),
    },
    key,
    encoder.encode(JSON.stringify(payload)),
  );
  return {
    iv: toBase64(iv),
    ciphertext: toBase64(new Uint8Array(ciphertext)),
    version: 1,
  };
}
export async function decryptMessage(key, record, uid) {
  if (record.version !== 1) throw new Error("Unsupported encryption version");
  const clear = await crypto.subtle.decrypt(
    {
      name: "AES-GCM",
      iv: fromBase64(record.iv),
      additionalData: encoder.encode(`${uid}/${record.deviceId}/${record.id}`),
    },
    key,
    fromBase64(record.ciphertext),
  );
  return JSON.parse(new TextDecoder().decode(clear));
}
export function extractOtp(body) {
  return /(?:otp|code|verification|one.time|password)/i.test(body)
    ? (body.match(/\b\d{4,8}\b/)?.[0] ?? null)
    : null;
}
