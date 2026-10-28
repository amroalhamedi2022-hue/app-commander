package com.appcommander.mobile.ui

import com.appcommander.mobile.model.ApkInspection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInspectionLoadStateTest {
    private fun apk(fileName: String) = ApkInspection(
        fileName = fileName,
        label = "Example",
        packageName = "com.example.app",
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 28,
        targetSdk = 35,
        permissions = emptyList(),
        activities = 0,
        services = 0,
        receivers = 0,
        providers = 0,
        sizeBytes = 1L,
        fileSha256 = "0".repeat(64),
        signerSha256 = emptyList(),
    )

    @Test fun beginningANewInspectionClearsOldResultBeforeNewUriCanBeTrusted() {
        val first = ApkInspectionLoadState().begin()
        val complete = first.complete(first.generation, apk("old.apk"))

        val next = complete.begin()

        assertTrue(next.loading)
        assertNull(next.result)
        assertNull(next.error)
        assertEquals(complete.generation + 1L, next.generation)
    }

    @Test fun cancellationStopsCurrentInspectionWithoutInventingAnError() {
        val started = ApkInspectionLoadState().begin()
        val cancelled = started.cancel(started.generation)

        assertFalse(cancelled.loading)
        assertNull(cancelled.result)
        assertNull(cancelled.error)
    }

    @Test fun staleCompletionFailureAndCancellationCannotOverwriteNewerInspection() {
        val older = ApkInspectionLoadState().begin()
        val newer = older.begin()
        val current = newer.complete(newer.generation, apk("new.apk"))

        assertSame(current, current.complete(older.generation, apk("stale.apk")))
        assertSame(current, current.fail(older.generation, "stale error"))
        assertSame(current, current.cancel(older.generation))
        assertEquals("new.apk", current.result?.fileName)
        assertFalse(current.loading)
        assertNull(current.error)
    }

    @Test fun failureLeavesNoPreviousInspectionAndAllowsRetry() {
        val started = ApkInspectionLoadState().begin()
        val failed = started.fail(started.generation, "invalid APK")

        assertFalse(failed.loading)
        assertNull(failed.result)
        assertEquals("invalid APK", failed.error)

        val retry = failed.begin()
        assertTrue(retry.loading)
        assertNull(retry.result)
        assertNull(retry.error)
    }
    @Test fun cancelledGenerationCannotLaterCompleteOrFail() {
        val started = ApkInspectionLoadState().begin()
        val cancelled = started.cancel(started.generation)

        assertSame(cancelled, cancelled.complete(started.generation, apk("late.apk")))
        assertSame(cancelled, cancelled.fail(started.generation, "late error"))
        assertSame(cancelled, cancelled.cancel(started.generation))
        assertNull(cancelled.result)
        assertNull(cancelled.error)
        assertFalse(cancelled.loading)
    }

    @Test fun completedGenerationAcceptsOnlyItsFirstTerminalTransition() {
        val started = ApkInspectionLoadState().begin()
        val completed = started.complete(started.generation, apk("accepted.apk"))

        assertSame(completed, completed.complete(started.generation, apk("duplicate.apk")))
        assertSame(completed, completed.fail(started.generation, "late failure"))
        assertSame(completed, completed.cancel(started.generation))
        assertEquals("accepted.apk", completed.result?.fileName)
        assertNull(completed.error)
        assertFalse(completed.loading)
    }

    @Test fun failedGenerationCannotBeRestoredByLateSuccessAndRetryStillWorks() {
        val started = ApkInspectionLoadState().begin()
        val failed = started.fail(started.generation, "invalid APK")

        assertSame(failed, failed.complete(started.generation, apk("late.apk")))
        assertSame(failed, failed.cancel(started.generation))
        assertSame(failed, failed.fail(started.generation, "second error"))
        assertNull(failed.result)
        assertEquals("invalid APK", failed.error)
        assertFalse(failed.loading)

        val retry = failed.begin()
        val success = retry.complete(retry.generation, apk("retry.apk"))
        assertEquals("retry.apk", success.result?.fileName)
        assertNull(success.error)
        assertFalse(success.loading)
    }

}
