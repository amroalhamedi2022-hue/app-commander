package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedInstallHandoffTest {
    @Rule @JvmField val temp = TemporaryFolder()

    @Test fun failedOrCancelledHandoffDiscardsTheRecordedSnapshotExactlyOnce() {
        val file = temp.newFile("verified.apk")
        val cancellation = CancellationException("cancelled before installer launch")
        val discarded = mutableListOf<File>()
        val causes = mutableListOf<Throwable>()
        val handoff = VerifiedInstallHandoff<File> { snapshot, failure ->
            discarded += snapshot
            causes += failure
        }

        // Mirrors the IO block recording ownership before its cancellable
        // withContext result is dispatched back to the UI coroutine.
        handoff.record(file)
        assertTrue(handoff.discardIfNotTransferred(cancellation))
        assertFalse(handoff.discardIfNotTransferred(cancellation))

        assertEquals(listOf(file), discarded)
        assertSame(cancellation, causes.single())
    }

    @Test fun successfulActivityLaunchTransfersSnapshotAndSkipsCleanup() {
        val file = temp.newFile("installer-reading.apk")
        val discarded = mutableListOf<File>()
        val handoff = VerifiedInstallHandoff<File> { snapshot, _ -> discarded += snapshot }

        handoff.record(file)
        assertTrue(handoff.markTransferred())
        assertFalse(handoff.discardIfNotTransferred(IllegalStateException("unused")))

        assertTrue(discarded.isEmpty())
        assertTrue("installer snapshot must remain available after launch", file.exists())
    }

    @Test fun cleanupFailureIsAttachedToThePrimaryFailureWithoutReplacingIt() {
        val file = temp.newFile("stuck.apk")
        val primary = IOException("installer launch failed")
        val cleanup = SecurityException("cache delete denied")
        val handoff = VerifiedInstallHandoff<File> { snapshot, failure ->
            ApkInstallSnapshot.deleteAfterFailure(snapshot, failure) { throw cleanup }
        }

        handoff.record(file)
        assertTrue(handoff.discardIfNotTransferred(primary))

        assertEquals("installer launch failed", primary.message)
        assertEquals(1, primary.suppressed.size)
        assertSame(cleanup, primary.suppressed.single())
        assertTrue(file.exists())
    }

    @Test fun nullPreparationDoesNotInventAFileToClean() {
        var cleanupCalls = 0
        val handoff = VerifiedInstallHandoff<String> { _, _ -> cleanupCalls++ }

        handoff.record(null)
        assertFalse(handoff.discardIfNotTransferred(IllegalStateException("no snapshot")))

        assertEquals(0, cleanupCalls)
    }

    @Test fun concurrentTransferAndCleanupHaveExactlyOneWinner() {
        repeat(64) { iteration ->
            val file = temp.newFile("race-$iteration.apk")
            val cleanupCount = AtomicInteger()
            val handoff = VerifiedInstallHandoff<File> { _, _ -> cleanupCount.incrementAndGet() }
            handoff.record(file)

            val start = CountDownLatch(1)
            val transferred = AtomicBoolean(false)
            val cleanupClaimed = AtomicBoolean(false)
            val transferThread = Thread {
                start.await()
                transferred.set(handoff.markTransferred())
            }
            val cleanupThread = Thread {
                start.await()
                cleanupClaimed.set(handoff.discardIfNotTransferred(IOException("race")))
            }
            transferThread.start()
            cleanupThread.start()
            start.countDown()
            transferThread.join()
            cleanupThread.join()

            assertTrue("exactly one terminal transition must win", transferred.get() xor cleanupClaimed.get())
            assertEquals(if (transferred.get()) 0 else 1, cleanupCount.get())
        }
    }

}
