package com.linksms.app
import org.junit.Assert.*
import org.junit.Test
class UnlockGateTest {
 @Test fun cancellationDoesNotLoopButManualRetryWorks() {
  val gate = UnlockGate()
  assertTrue(gate.begin(true)); gate.finished()
  assertFalse(gate.begin(true)); assertTrue(gate.begin(false))
 }
 @Test fun returningToAppPromptsAgain() {
  val gate = UnlockGate()
  assertTrue(gate.begin(true)); gate.finished(); gate.backgrounded()
  assertTrue(gate.begin(true))
 }
 @Test fun credentialActivityCannotCreateDuplicatePrompt() {
  val gate = UnlockGate()
  assertTrue(gate.begin(true)); gate.backgrounded()
  assertFalse(gate.begin(true)); gate.finished()
  assertFalse(gate.begin(true))
 }
 @Test fun manualLockStaysLockedUntilExplicitRetry() {
  val gate = UnlockGate(); gate.manualLock()
  assertFalse(gate.begin(true)); assertTrue(gate.begin(false))
 }
}
