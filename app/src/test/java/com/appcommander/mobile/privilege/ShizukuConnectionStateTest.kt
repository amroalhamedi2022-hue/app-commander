package com.appcommander.mobile.privilege

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShizukuConnectionStateTest {
    @Test fun staleBinderIsDiscardedAndTimeoutCanBeRetried() {
        val state = ShizukuConnectionState<Any>()
        val stale = Any()
        state.connected(stale)
        assertSame(stale, state.remoteCandidate())

        state.discardIfSame(stale)
        assertNull(state.remoteCandidate())
        val timedOut = state.beginOrJoin()
        assertTrue(timedOut.shouldBind)
        state.resetTimedOutAttempt(timedOut.waiter)
        assertFalse(state.isBinding())

        val retry = state.beginOrJoin()
        assertTrue("A dead cached Binder must not pin the controller in binding=true", retry.shouldBind)
        assertNotSame(timedOut.waiter, retry.waiter)
    }

    @Test fun stalePingCannotClearANewerLiveBinder() {
        val state = ShizukuConnectionState<Any>()
        val stale = Any()
        val live = Any()
        state.connected(stale)
        state.connected(live)

        state.discardIfSame(stale)

        assertSame("Identity-guarded cleanup must preserve the newer Binder",
            live, state.remoteCandidate())
    }

    @Test fun disconnectBindingDeathAndNullBindingFailWaitersAndAllowRetry() {
        for (fail in listOf<(ShizukuConnectionState<String>) -> Unit>(
            { it.failCurrent("disconnected") },
            { state ->
                val request = state.beginOrJoin()
                state.failIfCurrent(request.waiter, "binding died")
            },
            { state ->
                val request = state.beginOrJoin()
                state.failIfCurrent(request.waiter, "null binding")
            },
        )) {
            val state = ShizukuConnectionState<String>()
            val request = state.beginOrJoin()
            fail(state)
            assertTrue("The current wait must be completed on a failed bind", request.waiter.isCompleted)
            assertFalse(state.isBinding())
            assertTrue("A failed bind must permit an immediate retry", state.beginOrJoin().shouldBind)
        }
    }

    @Test fun oldTimeoutCannotResetANewerBindingAttempt() {
        val state = ShizukuConnectionState<String>()
        val old = state.beginOrJoin()
        state.resetTimedOutAttempt(old.waiter)

        val current = state.beginOrJoin()
        assertTrue(current.shouldBind)
        state.resetTimedOutAttempt(old.waiter)

        assertTrue(state.isBinding())
        assertTrue(state.isCurrent(current.waiter))
        state.connected("live-service")
        assertEquals("live-service", runBlocking { current.waiter.await() })
    }

    @Test fun successfulConnectionCompletesTheCurrentWaiter() {
        val state = ShizukuConnectionState<String>()
        val request = state.beginOrJoin()
        state.connected("service")
        assertTrue(request.waiter.isCompleted)
        assertEquals("service", runBlocking { request.waiter.await() })
        assertFalse(state.isBinding())
    }

    @Test fun controllerWiresPlatformBindingFailuresIntoRecoveryState() {
        val source = java.io.File(
            "src/main/java/com/appcommander/mobile/privilege/ShizukuController.kt",
        ).readText()
        assertTrue(source.contains("override fun onBindingDied"))
        assertTrue(source.contains("override fun onNullBinding"))
        assertTrue(source.contains("connectionState.failCurrent(\"Shizuku binding died\")"))
        assertTrue(source.contains("connectionState.failCurrent(\"Shizuku returned no service\")"))
        assertTrue(source.contains("connectionState.discardIfSame(candidate)"))
        assertTrue(source.contains("connectionState.resetTimedOutAttempt(request.waiter)"))
    }
}
