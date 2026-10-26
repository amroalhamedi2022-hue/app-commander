package com.appcommander.mobile.ui

import com.appcommander.mobile.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRefreshStateTest {
    private fun app(label: String) = AppEntry(
        label = label,
        packageName = "test.${label.lowercase()}",
        versionName = "1",
        targetSdk = 35,
        isSystem = false,
        enabled = true,
        lastUpdateTime = 1L,
    )

    @Test fun failedRefreshStopsLoadingAndPreservesLastSuccessfulInventory() {
        val cached = listOf(app("Cached"))
        val started = InventoryRefreshState(apps = cached).begin()

        val failed = started.fail(started.generation, "refresh failed")

        assertFalse(failed.loading)
        assertEquals(cached, failed.apps)
        assertEquals("refresh failed", failed.error)
    }

    @Test fun olderSuccessAndFailureCannotOverwriteNewerRefresh() {
        val first = InventoryRefreshState(apps = listOf(app("Old cache"))).begin()
        val second = first.begin()
        val newerResult = second.complete(second.generation, listOf(app("Newest")))

        assertSame(newerResult, newerResult.complete(first.generation, listOf(app("Stale"))))
        assertSame(newerResult, newerResult.fail(first.generation, "stale failure"))
        assertEquals(listOf("Newest"), newerResult.apps.map { it.label })
        assertFalse(newerResult.loading)
        assertNull(newerResult.error)
    }

    @Test fun cancellationStopsOnlyItsOwnSpinnerWithoutInventingAnError() {
        val cached = listOf(app("Cached"))
        val current = InventoryRefreshState(apps = cached).begin()
        val cancelled = current.cancel(current.generation)

        assertFalse(cancelled.loading)
        assertEquals(cached, cancelled.apps)
        assertNull(cancelled.error)

        val replacement = current.begin()
        assertSame(replacement, replacement.cancel(current.generation))
        assertTrue(replacement.loading)
    }

    @Test fun successfulEmptyInventoryIsNotAnError() {
        val started = InventoryRefreshState(apps = listOf(app("Old"))).begin()

        val completed = started.complete(started.generation, emptyList())

        assertFalse(completed.loading)
        assertTrue(completed.apps.isEmpty())
        assertNull(completed.error)
    }

    @Test fun beginningANewRefreshClearsPreviousErrorAndAdvancesGeneration() {
        val failed = InventoryRefreshState().begin().let {
            it.fail(it.generation, "temporary failure")
        }

        val retry = failed.begin()

        assertTrue(retry.generation > failed.generation)
        assertTrue(retry.loading)
        assertNull(retry.error)
    }
}
