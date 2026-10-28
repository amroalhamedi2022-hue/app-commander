package com.appcommander.mobile.privilege

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

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
    private val scheduledJobs = mutableMapOf<CompletableDeferred<T>, Job>()

    init {
        require(timeoutMillis > 0L) { "Binding timeout must be positive" }
    }

    fun schedule(request: BindingRequest<T>, onTimeout: () -> Unit): Job? {
        if (!request.shouldBind) return null

        // The timer must not cancel itself when its own timeout completes the
        // shared waiter. Other completion paths should cancel it immediately.
        val timeoutStarted = AtomicBoolean(false)
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                delay(timeoutMillis)
                timeoutStarted.set(true)
                val reset = synchronized(lock) {
                    state.resetTimedOutAttempt(request)
                }
                if (reset) onTimeout()
            } finally {
                synchronized(this@ShizukuBindingTimeout) {
                    if (scheduledJobs[request.waiter] === job) {
                        scheduledJobs.remove(request.waiter)
                    }
                }
            }
        }

        val existing = synchronized(this) {
            val current = scheduledJobs[request.waiter]
            if (current == null) scheduledJobs[request.waiter] = job
            current
        }
        if (existing != null) {
            job.cancel()
            return existing
        }

        request.waiter.invokeOnCompletion {
            if (!timeoutStarted.get()) cancel(request.waiter)
        }

        // A platform callback may have completed the waiter synchronously
        // before schedule() was reached. Do not leave a useless timer behind.
        val stillPending = synchronized(lock) {
            state.isCurrent(request.waiter) && state.isBinding()
        }
        if (!stillPending) {
            cancel(request.waiter)
            job.cancel()
            return null
        }

        job.start()
        if (job.isCancelled) {
            // A lazy job whose parent scope was already cancelled never enters
            // its body/finally block. Reset the still-current attempt here so
            // callers cannot remain bound to a waiter with no live timeout.
            val reset = synchronized(lock) {
                state.resetTimedOutAttempt(request)
            }
            synchronized(this) {
                if (scheduledJobs[request.waiter] === job) {
                    scheduledJobs.remove(request.waiter)
                }
            }
            if (reset) onTimeout()
            return null
        }
        return job
    }

    /** Cancel only the timer associated with this exact bind-attempt waiter. */
    fun cancel(waiter: CompletableDeferred<T>): Boolean {
        val job = synchronized(this) { scheduledJobs.remove(waiter) }
        job?.cancel()
        return job != null
    }
}
