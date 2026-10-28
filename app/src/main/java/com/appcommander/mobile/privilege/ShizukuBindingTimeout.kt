package com.appcommander.mobile.privilege

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the timeout for a shared Shizuku bind attempt independently of any one
 * caller waiting on its CompletableDeferred. Only the attempt owner schedules
 * this timer; stale/successful attempts become no-ops through state identity.
 */
internal class ShizukuBindingTimeout<T : Any>(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long,
    private val state: ShizukuConnectionState<T>,
    private val lock: Any,
) {
    init {
        require(timeoutMillis > 0L) { "Binding timeout must be positive" }
    }

    fun schedule(request: BindingRequest<T>, onTimeout: () -> Unit): Job? {
        if (!request.shouldBind) return null
        return scope.launch {
            delay(timeoutMillis)
            val reset = synchronized(lock) {
                state.resetTimedOutAttempt(request)
            }
            if (reset) onTimeout()
        }
    }
}
