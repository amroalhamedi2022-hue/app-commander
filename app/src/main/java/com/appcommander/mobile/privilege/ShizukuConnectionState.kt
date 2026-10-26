package com.appcommander.mobile.privilege

import kotlinx.coroutines.CompletableDeferred

/** Testable state machine for one Shizuku UserService binding lifecycle. */
internal data class BindingRequest<T : Any>(
    val waiter: CompletableDeferred<T>,
    val shouldBind: Boolean,
)

/**
 * Keeps Binder liveness, bind-in-progress state and the waiter for the current
 * attempt consistent. A timed-out older waiter must never reset a newer bind.
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

    @Synchronized fun connected(service: T) {
        remote = service
        binding = false
        if (!pending.isCompleted) pending.complete(service)
    }

    /** Used for disconnect, binding death, and a null service binding. */
    @Synchronized fun failCurrent(reason: String) {
        remote = null
        binding = false
        val failed = pending
        if (!failed.isCompleted) failed.completeExceptionally(IllegalStateException(reason))
        pending = CompletableDeferred()
    }

    @Synchronized fun failIfCurrent(waiter: CompletableDeferred<T>, reason: String) {
        if (pending !== waiter) return
        remote = null
        binding = false
        if (!waiter.isCompleted) waiter.completeExceptionally(IllegalStateException(reason))
        pending = CompletableDeferred()
    }

    /** A stale timeout is allowed to reset only its own still-current attempt. */
    @Synchronized fun resetTimedOutAttempt(waiter: CompletableDeferred<T>) {
        if (pending === waiter && remote == null) {
            binding = false
            pending = CompletableDeferred()
        }
    }

    @Synchronized fun isBinding(): Boolean = binding

    @Synchronized fun isCurrent(waiter: CompletableDeferred<T>): Boolean = pending === waiter
}
