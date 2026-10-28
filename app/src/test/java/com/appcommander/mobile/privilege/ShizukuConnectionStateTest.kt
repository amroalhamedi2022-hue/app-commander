package com.appcommander.mobile.privilege

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShizukuConnectionStateTest {
    @Test fun staleBinderIsDiscardedAndTimeoutCanBeRetried() {
        val state = ShizukuConnectionState<Any>()
        val first = state.beginOrJoin()
        val stale = Any()
        assertTrue(state.connectedIfCurrent(first.waiter, stale))
        assertSame(stale, state.remoteCandidate())

        state.discardIfSame(stale)
        assertNull(state.remoteCandidate())
        val timedOut = state.beginOrJoin()
        assertTrue(timedOut.shouldBind)
        assertTrue(state.resetTimedOutAttempt(timedOut))
        assertFalse(state.isBinding())

        val retry = state.beginOrJoin()
        assertTrue("A dead cached Binder must not pin the controller in binding=true", retry.shouldBind)
        assertNotSame(timedOut.waiter, retry.waiter)
    }

    @Test fun stalePingCannotClearANewerLiveBinder() {
        val state = ShizukuConnectionState<Any>()
        val first = state.beginOrJoin()
        val stale = Any()
        assertTrue(state.connectedIfCurrent(first.waiter, stale))
        assertTrue(state.failIfCurrent(first.waiter, "binder died"))
        val retry = state.beginOrJoin()
        val live = Any()
        assertTrue(state.connectedIfCurrent(retry.waiter, live))

        state.discardIfSame(stale)

        assertSame("Identity-guarded cleanup must preserve the newer Binder",
            live, state.remoteCandidate())
    }

    @Test fun disconnectBindingDeathAndNullBindingFailWaitersAndAllowRetry() {
        for (reason in listOf("disconnected", "binding died", "null binding")) {
            val state = ShizukuConnectionState<String>()
            val request = state.beginOrJoin()
            assertTrue(state.failIfCurrent(request.waiter, reason))
            assertTrue("The current wait must be completed on a failed bind", request.waiter.isCompleted)
            assertFalse(state.isBinding())
            assertTrue("A failed bind must permit an immediate retry", state.beginOrJoin().shouldBind)
        }
    }

    @Test fun oldTimeoutCannotResetANewerBindingAttempt() {
        val state = ShizukuConnectionState<String>()
        val old = state.beginOrJoin()
        assertTrue(state.resetTimedOutAttempt(old))

        val current = state.beginOrJoin()
        assertTrue(current.shouldBind)
        assertFalse(state.resetTimedOutAttempt(old))

        assertTrue(state.isBinding())
        assertTrue(state.isCurrent(current.waiter))
        assertTrue(state.connectedIfCurrent(current.waiter, "live-service"))
        assertEquals("live-service", runBlocking { current.waiter.await() })
    }

    @Test fun successfulConnectionCompletesTheCurrentWaiter() {
        val state = ShizukuConnectionState<String>()
        val request = state.beginOrJoin()
        assertTrue(state.connectedIfCurrent(request.waiter, "service"))
        assertTrue(request.waiter.isCompleted)
        assertEquals("service", runBlocking { request.waiter.await() })
        assertFalse(state.isBinding())
    }

    @Test fun lateConnectedCallbackFromOldAttemptCannotReplaceNewBinder() {
        val state = ShizukuConnectionState<String>()
        val old = state.beginOrJoin()
        assertTrue(state.connectedIfCurrent(old.waiter, "old-service"))
        assertTrue(state.failIfCurrent(old.waiter, "old service disconnected"))

        val current = state.beginOrJoin()
        assertFalse(state.connectedIfCurrent(old.waiter, "late-old-service"))
        assertTrue(state.isBinding())
        assertFalse(current.waiter.isCompleted)

        assertTrue(state.connectedIfCurrent(current.waiter, "new-service"))
        assertFalse(state.connectedIfCurrent(old.waiter, "duplicate-old-service"))
        assertEquals("new-service", state.remoteCandidate())
    }

    @Test fun lateFailureCallbackFromOldAttemptCannotFailNewWaiter() {
        val state = ShizukuConnectionState<String>()
        val old = state.beginOrJoin()
        assertTrue(state.failIfCurrent(old.waiter, "old bind failed"))
        val current = state.beginOrJoin()

        assertFalse(state.failIfCurrent(old.waiter, "late old binding death"))
        assertTrue(state.isCurrent(current.waiter))
        assertTrue(state.isBinding())
        assertFalse("Late callback must not complete the new waiter", current.waiter.isCompleted)

        assertTrue(state.connectedIfCurrent(current.waiter, "new-service"))
        assertEquals("new-service", runBlocking { current.waiter.await() })
    }

    @Test fun joinerTimeoutCannotResetOwnerBindAndOwnerTimeoutWakesJoiners() {
        val state = ShizukuConnectionState<String>()
        val owner = state.beginOrJoin()
        val joiner = state.beginOrJoin()
        assertTrue(owner.shouldBind)
        assertFalse(joiner.shouldBind)
        assertSame(owner.waiter, joiner.waiter)

        assertFalse("A joiner must not cancel the owner's shared binding",
            state.resetTimedOutAttempt(joiner))
        assertTrue(state.isBinding())
        assertTrue(state.isCurrent(owner.waiter))

        assertTrue("The bind owner may reset its own timed-out attempt",
            state.resetTimedOutAttempt(owner))
        assertFalse(state.isBinding())
        assertTrue("All callers sharing the failed attempt must wake promptly",
            joiner.waiter.isCompleted)
        assertFalse(state.isCurrent(owner.waiter))
    }

    @Test fun controllerScopesPlatformCallbacksToOneAttemptAndUnbindsOnlyThatConnection() {
        val source = java.io.File(
            "src/main/java/com/appcommander/mobile/privilege/ShizukuController.kt",
        ).readText()
        assertTrue(source.contains("connectionFor(request.waiter, serviceArgs)"))
        assertTrue(source.contains("connectionState.connectedIfCurrent(waiter, connected)"))
        assertTrue(source.contains("connectionState.failIfCurrent(waiter, \"Shizuku binding died\")"))
        assertTrue(source.contains("connectionState.failIfCurrent(waiter, \"Shizuku returned no service\")"))
        assertTrue(source.contains("Shizuku.unbindUserService(serviceArgs, connection, false)"))
        assertTrue(source.contains("bindingTimeout.schedule(request)"))
        assertTrue(source.contains("withTimeout(BIND_TIMEOUT_MILLIS)"))
        assertFalse("Caller cancellation must not own shared bind cleanup",
            source.contains("connectionState.resetTimedOutAttempt(request)"))
    }
    @Test fun bindTimeoutIsOwnedByTheSharedAttemptNotTheAwaitingCoroutine() {
        val controller = java.io.File(
            "src/main/java/com/appcommander/mobile/privilege/ShizukuController.kt",
        ).readText()
        val timeout = java.io.File(
            "src/main/java/com/appcommander/mobile/privilege/ShizukuBindingTimeout.kt",
        ).readText()

        assertTrue(controller.contains("bindingTimeout.schedule(request)"))
        assertTrue(controller.contains("withTimeout(BIND_TIMEOUT_MILLIS)"))
        assertFalse(controller.contains("connectionState.resetTimedOutAttempt(request)"))
        assertTrue(timeout.contains("delay(timeoutMillis)"))
        assertTrue(timeout.contains("state.resetTimedOutAttempt(request)"))
    }

    @Test fun duplicateConnectedCallbackCannotReplaceTheLiveBinderForSameAttempt() {
        val state = ShizukuConnectionState<Any>()
        val request = state.beginOrJoin()
        val live = Any()
        val duplicate = Any()

        assertTrue(state.connectedIfCurrent(request.waiter, live))
        assertTrue(state.connectedIfCurrent(request.waiter, duplicate))

        assertSame("duplicate callback must not replace the active Binder", live, state.remoteCandidate())
        assertSame(live, runBlocking { request.waiter.await() })
        assertFalse(state.isBinding())
    }

    @Test fun beginOrJoinReusesBinderInstalledBetweenInitialProbeAndJoin() {
        val state = ShizukuConnectionState<Any>()
        val original = state.beginOrJoin()
        val live = Any()
        assertTrue(state.connectedIfCurrent(original.waiter, live))

        // Models the race where the caller's remoteCandidate() returned null,
        // then the callback connected before beginOrJoin acquired the lock.
        val raced = state.beginOrJoin()

        assertFalse("a live Binder must not trigger a redundant bind", raced.shouldBind)
        assertSame(original.waiter, raced.waiter)
        assertSame(live, runBlocking { raced.waiter.await() })
        assertSame(live, state.remoteCandidate())
    }

}
