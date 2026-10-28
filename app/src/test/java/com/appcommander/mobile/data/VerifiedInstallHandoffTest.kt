package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
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
        handoff.discardIfNotTransferred(cancellation)
        handoff.discardIfNotTransferred(cancellation)

        assertEquals(listOf(file), discarded)
        assertSame(cancellation, causes.single())
    }

    @Test fun successfulActivityLaunchTransfersSnapshotAndSkipsCleanup() {
        val file = temp.newFile("installer-reading.apk")
        val discarded = mutableListOf<File>()
        val handoff = VerifiedInstallHandoff<File> { snapshot, _ -> discarded += snapshot }

        handoff.record(file)
        handoff.markTransferred()
        handoff.discardIfNotTransferred(IllegalStateException("unused"))

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
        handoff.discardIfNotTransferred(primary)

        assertSame(primary, primary)
        assertEquals(1, primary.suppressed.size)
        assertSame(cleanup, primary.suppressed.single())
        assertTrue(file.exists())
    }

    @Test fun nullPreparationDoesNotInventAFileToClean() {
        var cleanupCalls = 0
        val handoff = VerifiedInstallHandoff<String> { _, _ -> cleanupCalls++ }

        handoff.record(null)
        handoff.discardIfNotTransferred(IllegalStateException("no snapshot"))

        assertEquals(0, cleanupCalls)
    }
}
