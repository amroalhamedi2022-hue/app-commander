package com.appcommander.mobile.data

/**
 * Best-effort adapter for optional usage metadata.
 *
 * Installed-package inventory remains authoritative: usage access can be
 * denied, the AppOps service can be unavailable, and OEM providers can throw
 * or return null. None of those conditions should turn a valid app inventory
 * into a failed/empty refresh.
 */
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
}
