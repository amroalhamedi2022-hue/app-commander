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

    @Test fun successfulConnectionMakesScheduledTimeoutANoop() = runBlocking {
        val state = ShizukuConnectionState<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val timeout = ShizukuBindingTimeout(scope, 100L, state, Any())
        val unbindCount = AtomicInteger()
        try {
            val request = state.beginOrJoin()
            val timer = timeout.schedule(request) { unbindCount.incrementAndGet() }
            assertTrue(state.connectedIfCurrent(request.waiter, "live-service"))
            assertEquals("live-service", request.waiter.await())

            timer?.join()
            assertEquals("live-service", state.remoteCandidate())
            assertEquals(0, unbindCount.get())
            assertFalse(state.isBinding())
        } finally {
            scope.cancel()
        }
    }
}
