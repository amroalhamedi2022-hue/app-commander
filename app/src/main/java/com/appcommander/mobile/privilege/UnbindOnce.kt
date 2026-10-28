package com.appcommander.mobile.privilege

/**
 * Serializes cleanup for one platform connection. Successful cleanup is
 * idempotent; a thrown platform exception leaves the gate retryable. The
 * in-progress flag also prevents same-thread reentrant callbacks from invoking
 * the platform cleanup recursively.
 */
internal class UnbindOnce {
    private var completed = false
    private var inProgress = false

    @Synchronized
    fun run(action: () -> Unit): Boolean {
        if (completed || inProgress) return false
        inProgress = true
        try {
            action()
            completed = true
            return true
        } finally {
            inProgress = false
        }
    }
}
