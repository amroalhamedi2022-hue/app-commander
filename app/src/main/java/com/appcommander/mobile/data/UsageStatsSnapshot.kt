package com.appcommander.mobile.data

/**
 * Best-effort adapter for optional usage metadata.
 *
 * Installed-package inventory remains authoritative: usage access can be
 * denied, the AppOps service can be unavailable, and OEM providers can throw
 * or return null. None of those conditions should turn a valid app inventory
 * into a failed/empty refresh.
 */
internal data class UsageStatsValue(
    val totalTimeInForegroundMillis: Long,
    val lastTimeUsedMillis: Long,
)

internal object UsageStatsSnapshot {
    const val LOOKBACK_MILLIS = 24L * 60L * 60L * 1000L

    fun <T : Any> load(
        nowMillis: Long,
        hasAccess: () -> Boolean,
        query: (beginTimeMillis: Long, endTimeMillis: Long) -> Map<String, T>?,
    ): Map<String, T> {
        val end = nowMillis.coerceAtLeast(0L)
        if (end == 0L || !runCatching { hasAccess() }.getOrDefault(false)) {
            return emptyMap()
        }
        val begin = (end - LOOKBACK_MILLIS).coerceAtLeast(0L)
        return runCatching { query(begin, end) }.getOrNull().orEmpty()
    }

    /**
     * Normalize optional provider values before they reach UI models.
     * Foreground duration is limited to the queried window and elapsed time;
     * last-used is an epoch timestamp, never a duration.
     */
    fun sanitize(
        nowMillis: Long,
        snapshot: Map<String, UsageStatsValue>,
    ): Map<String, UsageStatsValue> {
        val end = nowMillis.coerceAtLeast(0L)
        if (end == 0L) return emptyMap()
        val maxForeground = minOf(LOOKBACK_MILLIS, end)
        return snapshot.asSequence()
            .filter { (packageName, _) -> packageName.isNotBlank() }
            .associate { (packageName, value) ->
                packageName to UsageStatsValue(
                    totalTimeInForegroundMillis = value.totalTimeInForegroundMillis
                        .coerceIn(0L, maxForeground),
                    lastTimeUsedMillis = value.lastTimeUsedMillis.coerceIn(0L, end),
                )
            }
    }
}
