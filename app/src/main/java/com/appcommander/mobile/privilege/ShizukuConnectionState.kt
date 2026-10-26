package com.appcommander.mobile.privilege

import kotlinx.coroutines.CompletableDeferred

/** Testable state machine for one Shizuku UserService binding lifecycle. */
internal data class BindingRequest<T : Any>(
    val waiter: CompletableDeferred<T>,
    val shouldBind: Boolean,
)

/**
 * Keeps Binder liveness, bind-in-progress state and the waiter for the current
 * attempt consistent. Every callback must present its attempt's waiter so a
 * delayed callback cannot mutate a newer bind attempt.
 */
internal class ShizukuConnectionState<T : Any> {
    @Volatile private var remote: T? = null
    @Volatile private var binding = false
    @Volatile private var pending = CompletableDeferred<T>()

    @Synchronized fun remoteCandidate(): T? = remote

    /** Discard only the same instance that was pinged and found dead. */
    @Synchronized fun discardIfSame(candidate: T) {
        if (remote === candidate) remote = null
    }

    @Synchronized fun beginOrJoin(): BindingRequest<T> {
        if (binding) return BindingRequest(pending, shouldBind = false)
        binding = true
        pending = CompletableDeferred()
        return BindingRequest(pending, shouldBind = true)
    }

    /**
     * Accept a connection only while its exact bind attempt is still current.
     * Returns false for callbacks delivered after timeout, failure or retry.
     */
    @Synchronized fun connectedIfCurrent(waiter: CompletableDeferred<T>, service: T): Boolean {
        if (pending !== waiter) return false
        remote = service
        binding = false
        if (!waiter.isCompleted) waiter.complete(service)
        return true
    }

    /** Used for disconnect, binding death, null binding and stale callback isolation. */
    @Synchronized fun failIfCurrent(waiter: CompletableDeferred<T>, reason: String): Boolean {
        if (pending !== waiter) return false
        remote = null
        binding = false
        if (!waiter.isCompleted) waiter.completeExceptionally(IllegalStateException(reason))
        pending = CompletableDeferred()
        return true
    }

    /**
     * Only the bind owner may reset an attempt on timeout. Joiners share the
     * same waiter, but their local timeout must not cancel somebody else's bind.
     * Complete the shared waiter exceptionally so all joiners fail promptly.
     */
    @Synchronized fun resetTimedOutAttempt(request: BindingRequest<T>): Boolean {
        if (!request.shouldBind) return false
        val waiter = request.waiter
        if (pending === waiter && remote == null) {
            binding = false
            if (!waiter.isCompleted) {
                waiter.completeExceptionally(IllegalStateException("Shizuku bind attempt timed out"))
            }
            pending = CompletableDeferred()
            return true
        }
        return false
    }

    @Synchronized fun isBinding(): Boolean = binding

    @Synchronized fun isCurrent(waiter: CompletableDeferred<T>): Boolean = pending === waiter
}
