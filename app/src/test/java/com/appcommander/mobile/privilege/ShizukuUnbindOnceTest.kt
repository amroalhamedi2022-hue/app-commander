package com.appcommander.mobile.privilege

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ShizukuUnbindOnceTest {
    @Test fun successfulCleanupIsIdempotentAcrossLateCallbackAndTimeout() {
        val gate = UnbindOnce()
        val platformCalls = AtomicInteger()

        assertTrue(gate.run { platformCalls.incrementAndGet() })
        assertFalse(gate.run { platformCalls.incrementAndGet() })
        assertFalse(gate.run { platformCalls.incrementAndGet() })

        assertEquals(1, platformCalls.get())
    }

    @Test fun failedCleanupCanBeRetriedAndThenBecomesIdempotent() {
        val gate = UnbindOnce()
        val attempts = AtomicInteger()

        assertThrows(IllegalStateException::class.java) {
            gate.run {
                attempts.incrementAndGet()
                throw IllegalStateException("simulated platform unbind failure")
            }
        }
        assertTrue(gate.run { attempts.incrementAndGet() })
        assertFalse(gate.run { attempts.incrementAndGet() })
        assertEquals(2, attempts.get())
    }

    @Test fun concurrentCleanupRequestsInvokePlatformActionAtMostOnce() = runBlocking {
        val gate = UnbindOnce()
        val platformCalls = AtomicInteger()

        coroutineScope {
            repeat(32) {
                launch(Dispatchers.Default) {
                    gate.run { platformCalls.incrementAndGet() }
                }
            }
        }

        assertEquals(1, platformCalls.get())
    }

    @Test fun timeoutAndCallbacksShareOneGateAndNeverKillTheUserService() {
        val source = File(
            "src/main/java/com/appcommander/mobile/privilege/ShizukuController.kt",
        ).readText()

        assertTrue(source.contains("private val unbindGates = java.util.WeakHashMap<ServiceConnection, UnbindOnce>()"))
        assertTrue(source.contains("unbindGates.getOrPut(connection) { UnbindOnce() }"))
        assertTrue(source.contains("gate.run { Shizuku.unbindUserService(serviceArgs, connection, false) }"))
        assertTrue(source.contains("bindingTimeout.schedule(request) { unbind(serviceArgs, attempt) }"))
        assertFalse(source.contains("Shizuku.unbindUserService(serviceArgs, connection, true)"))
        assertEquals(1, Regex("Shizuku\\.unbindUserService\\(").findAll(source).count())
    }
}
