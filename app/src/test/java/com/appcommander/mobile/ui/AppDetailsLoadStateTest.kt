package com.appcommander.mobile.ui

import com.appcommander.mobile.model.AppDetails
import com.appcommander.mobile.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetailsLoadStateTest {
    private fun details(label: String): AppDetails {
        val entry = AppEntry(
            label = label,
            packageName = "test.${label.lowercase().replace(' ', '.')}",
            versionName = "1",
            targetSdk = 35,
            isSystem = false,
            enabled = true,
            lastUpdateTime = 1L,
        )
        return AppDetails(
            app = entry,
            permissions = emptyList(),
            activities = emptyList(),
            services = emptyList(),
            receivers = emptyList(),
            apkPath = "",
        )
    }

    @Test fun initialFailureStopsSpinnerAndExposesRetryableError() {
        val started = AppDetailsLoadState().begin()
        val failed = started.fail(started.generation, "details unavailable")

        assertFalse(failed.loading)
        assertNull(failed.details)
        assertEquals("details unavailable", failed.error)
    }

    @Test fun failedRefreshPreservesLastSuccessfulDetails() {
        val first = AppDetailsLoadState().begin()
        val loadedDetails = details("Previously loaded")
        val loaded = first.complete(first.generation, loadedDetails)
        val refresh = loaded.begin()
        val failed = refresh.fail(refresh.generation, "temporary provider failure")

        assertSame(loadedDetails, failed.details)
        assertFalse(failed.loading)
        assertEquals("temporary provider failure", failed.error)
    }

    @Test fun staleSuccessAndFailureCannotOverwriteNewerDetails() {
        val first = AppDetailsLoadState().begin()
        val second = first.begin()
        val newestDetails = details("Newest")
        val newest = second.complete(second.generation, newestDetails)

        assertSame(newest, newest.complete(first.generation, details("Stale")))
        assertSame(newest, newest.fail(first.generation, "stale failure"))
        assertEquals(newestDetails, newest.details)
        assertFalse(newest.loading)
        assertNull(newest.error)
    }

    @Test fun retryClearsPreviousErrorAndCancellationDoesNotInventOne() {
        val started = AppDetailsLoadState().begin()
        val failed = started.fail(started.generation, "temporary failure")
        val retry = failed.begin()

        assertTrue(retry.loading)
        assertNull(retry.error)
        val cancelled = retry.cancel(retry.generation)
        assertFalse(cancelled.loading)
        assertNull(cancelled.error)
        assertNull(cancelled.details)
    }

    @Test fun staleCancellationCannotStopNewerSpinner() {
        val first = AppDetailsLoadState().begin()
        val second = first.begin()
        assertSame(second, second.cancel(first.generation))
        assertTrue(second.loading)
    }
}
