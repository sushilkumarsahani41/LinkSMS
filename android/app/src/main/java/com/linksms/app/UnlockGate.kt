package com.linksms.app

/** One system prompt per foreground visit; cancellation never loops. */
class UnlockGate {
    var promptActive = false
        private set
    private var attempted = false
    fun begin(automatic: Boolean): Boolean {
        if (promptActive || (automatic && attempted)) return false
        promptActive = true
        attempted = true
        return true
    }
    fun finished() { promptActive = false }
    fun backgrounded() { if (!promptActive) attempted = false }
    fun manualLock() { attempted = true }
    fun signedIn() { attempted = false }
}
