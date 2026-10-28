package com.appcommander.mobile.data

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks ownership of a prepared install snapshot across a cancellable
 * dispatcher hop. The UI records the handle before withContext returns and
 * transfers ownership only after startActivity succeeds.
 */
internal class VerifiedInstallHandoff<T>(
    private val discard: (T, Throwable) -> Unit,
) {
    private val prepared = AtomicReference<T?>(null)
    private val transferred = AtomicBoolean(false)
    private val cleanupAttempted = AtomicBoolean(false)

    fun record(snapshot: T?) {
        if (snapshot == null) return
        check(prepared.compareAndSet(null, snapshot)) {
            "An install snapshot was already recorded for this handoff"
        }
    }

    fun markTransferred() {
        transferred.set(true)
    }

    fun discardIfNotTransferred(failure: Throwable) {
        if (transferred.get() || !cleanupAttempted.compareAndSet(false, true)) return
        prepared.get()?.let { discard(it, failure) }
    }
}
