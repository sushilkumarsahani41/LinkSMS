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
// Keep these patterns identical to MessageCodec in the Android app (tests/otp-cases.json covers both).
const OTP_KEYWORD = /otp|code|verification|one.time|password/gi;
const OTP_CANDIDATE = /(?<![\p{L}\p{N}_.,])(?:\d{3}[- ]\d{3}|\d{4,8})(?![\p{L}\p{N}_]|[.,]\d)/gu;
const AMOUNT_PREFIX = /(?:rs\.?|inr|usd|eur|gbp|[₹$€£])\s*$/i;
/** Returns the 4–8 digit code nearest a code keyword, preferring one that follows it; skips amounts. */
export function extractOtp(body) {
  if (!body) return null;
  const keywords = [...body.matchAll(OTP_KEYWORD)];
  if (!keywords.length) return null;
  let best = null,
    bestScore = Infinity;
  for (const c of body.matchAll(OTP_CANDIDATE)) {
    if (AMOUNT_PREFIX.test(body.slice(Math.max(0, c.index - 5), c.index))) continue;
    const end = c.index + c[0].length;
    for (const k of keywords) {
      const kEnd = k.index + k[0].length;
      const score = c.index >= kEnd ? c.index - kEnd : k.index >= end ? (k.index - end) * 2 : Infinity;
      if (score < bestScore) [best, bestScore] = [c[0], score];
    }
  }
  return best?.replace(/[- ]/g, "") ?? null;
}
