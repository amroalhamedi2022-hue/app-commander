package com.appcommander.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsSnapshotTest {
    @Test fun deniedAccessReturnsEmptySnapshotWithoutQueryingProvider() {
        var queried = false
        val result = UsageStatsSnapshot.load(
            nowMillis = 100_000L,
            hasAccess = { false },
            query = { _, _ -> queried = true; mapOf("org.example" to 12) },
        )

        assertTrue(result.isEmpty())
        assertFalse(queried)
    }

    @Test fun appOpsFailureIsTreatedAsUnavailableOptionalMetadata() {
        var queried = false
        val result = UsageStatsSnapshot.load<Int>(
            nowMillis = 100_000L,
            hasAccess = { throw SecurityException("AppOps unavailable") },
            query = { _, _ -> queried = true; mapOf("org.example" to 12) },
        )

        assertTrue(result.isEmpty())
        assertFalse(queried)
    }

    @Test fun usageProviderFailureDoesNotEscapeIntoInventoryRefresh() {
        val result = UsageStatsSnapshot.load<Int>(
            nowMillis = 100_000L,
            hasAccess = { true },
            query = { _, _ -> throw IllegalStateException("UsageStatsManager failed") },
        )

        assertTrue(result.isEmpty())
    }

    @Test fun nullProviderResultIsAnEmptySnapshot() {
        val result = UsageStatsSnapshot.load<Int>(
            nowMillis = 100_000L,
            hasAccess = { true },
            query = { _, _ -> null },
        )

        assertTrue(result.isEmpty())
    }

    @Test fun successfulSnapshotAndTwentyFourHourWindowArePreserved() {
        val now = 172_800_123L
        var receivedWindow: Pair<Long, Long>? = null
        val expected = mapOf("org.example.one" to 25, "org.example.two" to 80)

        val result = UsageStatsSnapshot.load(
            nowMillis = now,
            hasAccess = { true },
            query = { begin, end ->
                receivedWindow = begin to end
                expected
            },
        )

        assertEquals(expected, result)
        assertEquals((now - UsageStatsSnapshot.LOOKBACK_MILLIS) to now, receivedWindow)
    }

    @Test fun lookbackClampsAtEpochAndZeroClockSkipsExternalCalls() {
        var accessChecks = 0
        var queryWindow: Pair<Long, Long>? = null
        val nearEpoch = UsageStatsSnapshot.load<Int>(
            nowMillis = 50L,
            hasAccess = { true },
            query = { begin, end -> queryWindow = begin to end; emptyMap() },
        )
        assertTrue(nearEpoch.isEmpty())
        assertEquals(0L to 50L, queryWindow)

        val zeroClock = UsageStatsSnapshot.load<Int>(
            nowMillis = 0L,
            hasAccess = { accessChecks++; true },
            query = { _, _ -> throw AssertionError("must not query with an invalid clock") },
        )
        assertTrue(zeroClock.isEmpty())
        assertEquals(0, accessChecks)
    }
}
