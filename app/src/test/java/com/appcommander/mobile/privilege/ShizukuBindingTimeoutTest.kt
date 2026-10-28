package com.appcommander.mobile.privilege

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ShizukuBindingTimeoutTest {
    @Test fun cancellingBindOwnerDoesNotCancelSharedAttemptBeforeItsIndependentTimeout() = runBlocking {
        val state = ShizukuConnectionState<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lock = Any()
        val timeout = ShizukuBindingTimeout(scope, 250L, state, lock)
        val unbindCount = AtomicInteger()
        try {
            val owner = state.beginOrJoin()
            val timer = timeout.schedule(owner) { unbindCount.incrementAndGet() }
            val joiner = state.beginOrJoin()
            assertTrue(owner.shouldBind)
            assertFalse(joiner.shouldBind)
            assertSame(owner.waiter, joiner.waiter)

            val ownerCaller = launch { owner.waiter.await() }
            ownerCaller.cancelAndJoin()

            assertTrue("the bind remains in progress after its caller leaves", state.isBinding())
            assertFalse("caller cancellation must not complete the shared waiter", owner.waiter.isCompleted)
            assertEquals(0, unbindCount.get())

            val joinerFailure = async {
                try {
                    joiner.waiter.await()
                    null
                } catch (failure: Exception) {
                    failure
                }
            }
            val failure = withTimeout(1_500L) { joinerFailure.await() }
            assertTrue(failure is IllegalStateException)
            timer?.join()
            assertEquals("only the independent owner timeout unbinds", 1, unbindCount.get())
            assertFalse(state.isBinding())
            assertTrue("a timed-out attempt must permit a fresh bind", state.beginOrJoin().shouldBind)
        } finally {
            scope.cancel()
        }
    }

    @Test fun successfulConnectionCancelsScheduledTimeoutImmediately() = runBlocking {
        val state = ShizukuConnectionState<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val timeout = ShizukuBindingTimeout(scope, 2_000L, state, Any())
        val unbindCount = AtomicInteger()
        try {
            val request = state.beginOrJoin()
            val timer = requireNotNull(timeout.schedule(request) { unbindCount.incrementAndGet() })
            assertTrue(state.connectedIfCurrent(request.waiter, "live-service"))
            assertEquals("live-service", request.waiter.await())

            withTimeout(500L) { timer.join() }
            assertTrue("successful completion should cancel the delayed job", timer.isCancelled)
            assertEquals("live-service", state.remoteCandidate())
            assertEquals(0, unbindCount.get())
            assertFalse(state.isBinding())
        } finally {
            scope.cancel()
        }
    }

    @Test fun failedCurrentBindingCancelsTimerAndAllowsRetry() = runBlocking {
        val state = ShizukuConnectionState<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val timeout = ShizukuBindingTimeout(scope, 2_000L, state, Any())
        val unbindCount = AtomicInteger()
        try {
            val request = state.beginOrJoin()
            val timer = requireNotNull(timeout.schedule(request) { unbindCount.incrementAndGet() })
            assertTrue(state.failIfCurrent(request.waiter, "simulated disconnect"))

            withTimeout(500L) { timer.join() }
            assertTrue("failure should cancel its no-longer-needed timer", timer.isCancelled)
            assertEquals(0, unbindCount.get())
            assertTrue("failed binding must still be retryable", state.beginOrJoin().shouldBind)
        } finally {
            scope.cancel()
        }
    }

    @Test fun synchronousConnectionBeforeSchedulingCreatesNoTimer() = runBlocking {
        val state = ShizukuConnectionState<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val timeout = ShizukuBindingTimeout(scope, 2_000L, state, Any())
        val unbindCount = AtomicInteger()
        try {
            val request = state.beginOrJoin()
            assertTrue(state.connectedIfCurrent(request.waiter, "already-live"))
            val timer = timeout.schedule(request) { unbindCount.incrementAndGet() }

            assertNull("completed synchronous bind should not schedule a timer", timer)
            assertEquals(0, unbindCount.get())
            assertEquals("already-live", state.remoteCandidate())
        } finally {
            scope.cancel()
        }
    }
}
