package com.linksms.app
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class MessageCodecTest {
 private val key = SecretKeySpec(Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="), "AES")
 private val iv = "KWn8zBlno+X7Sex+"
 private val ciphertext = "toV9OWB/DelQTINQgqmUDx+lPUpd9jb1fzpJUz8RX9H8GlbnFCPExcu+e2yt/KeKLiIX7VcgbQEP2+2eYl6Cg145rEPXzWzkhMo6HuxW3sj5tlnOqZTG4hoEONgbXRdCYL+J13w53Ae8walKQAxq/CQP1vfqwKjEgls+2WA="
 @Test fun decryptsBrowserFixtureIncludingSimAndUnicode() {
  val result = MessageCodec.decryptPayload(key, "fixture-user", "fixture-device", "fixture-message", iv, ciphertext)
  assertEquals("Your code is 123456. नमस्ते", result.getString("body"))
  assertEquals("SIM 2 · Work", result.getString("sim"))
  assertEquals("Test model", result.getString("model"))
 }
 @Test fun rejectsMovedCiphertext() {
  assertThrows(Exception::class.java) { MessageCodec.decryptPayload(key, "other-user", "fixture-device", "fixture-message", iv, ciphertext) }
 }
 @Test fun onlyExtractsCodesFromOtpLikeMessages() {
  assertEquals("123456", MessageCodec.otp("Your verification code is 123456"))
  assertNull(MessageCodec.otp("Flight 123456 has arrived"))
 }
}
