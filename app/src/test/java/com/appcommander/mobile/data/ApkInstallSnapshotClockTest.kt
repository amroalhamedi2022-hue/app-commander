package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkInstallSnapshotClockTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun negativeClockCannotOverflowCutoffAndDeleteVerifiedSnapshot() {
        val directory = temp.newFolder("negative-clock")
        val stillNeeded = directory.resolve("verified-still-needed.apk").apply {
            writeText("verified bytes")
            assertTrue(setLastModified(1_000L))
        }

        // Before the fix, Long.MIN_VALUE - MAX_AGE wrapped to a large positive
        // cutoff, causing this current snapshot to be deleted.
        assertEquals(
            0,
            ApkInstallSnapshot.cleanupExpired(
                directory, nowMillis = Long.MIN_VALUE,
                maxAgeMillis = ApkInstallSnapshot.MAX_AGE_MILLIS,
            ),
        )
        assertTrue(stillNeeded.exists())
    }

    @Test fun nearEpochClockAndRetentionLongerThanUptimeFailClosed() {
        val directory = temp.newFolder("near-epoch")
        val snapshot = directory.resolve("verified-keep.apk").apply {
            writeText("keep")
            assertTrue(setLastModified(0L))
        }

        assertEquals(0, ApkInstallSnapshot.cleanupExpired(directory, nowMillis = 0L))
        assertEquals(
            0,
            ApkInstallSnapshot.cleanupExpired(directory, nowMillis = 10L, maxAgeMillis = 1_000L),
        )
        assertTrue(snapshot.exists())
    }

    @Test fun extremePositiveRetentionIntervalCannotWrapCutoff() {
        val directory = temp.newFolder("extreme-retention")
        val snapshot = directory.resolve("verified-keep.apk").apply {
            writeText("keep")
            assertTrue(setLastModified(1_000L))
        }

        assertEquals(
            0,
            ApkInstallSnapshot.cleanupExpired(
                directory, nowMillis = Long.MAX_VALUE, maxAgeMillis = Long.MAX_VALUE,
            ),
        )
        assertTrue(snapshot.exists())
    }
}
