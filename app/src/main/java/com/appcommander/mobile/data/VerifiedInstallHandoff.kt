package com.appcommander.mobile.data

/**
 * Linearizable ownership state for a verified APK snapshot during installer handoff.
 * Transfer and cleanup race through one lock, so the file cannot be both transferred
 * and claimed for cleanup. This tracks ownership only; it does not verify APK signatures.
 */
internal class VerifiedInstallHandoff<T>(
    private val discard: (T, Throwable) -> Unit,
) {
    private enum class State { EMPTY, PREPARED, TRANSFERRED, CLEANUP_CLAIMED, CLEANED }

    private val lock = Any()
    private var prepared: T? = null
    private var state = State.EMPTY

    fun record(snapshot: T?) {
        if (snapshot == null) return
        synchronized(lock) {
            check(state == State.EMPTY) {
                "An install snapshot was already recorded or finalized for this handoff"
            }
            prepared = snapshot
            state = State.PREPARED
        }
    }

    /** Returns true only when this call atomically takes ownership away from cleanup. */
    fun markTransferred(): Boolean = synchronized(lock) {
        if (state != State.PREPARED) {
            false
        } else {
            state = State.TRANSFERRED
            prepared = null
            true
        }
    }

    /** Claims cleanup exactly once; the callback runs outside the lock. */
    fun discardIfNotTransferred(failure: Throwable): Boolean {
        val target = synchronized(lock) {
            if (state != State.PREPARED) return false
            state = State.CLEANUP_CLAIMED
            prepared.also { prepared = null }
        } ?: return false

        try {
            discard(target, failure)
        } finally {
            synchronized(lock) {
                if (state == State.CLEANUP_CLAIMED) state = State.CLEANED
            }
        }
        return true
    }
}
