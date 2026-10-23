package com.appcommander.mobile.data

/**
 * SAF display names come from untrusted providers. Never show control/bidi
 * spoofing characters or unbounded provider strings in the inspector.
 * This is a UI label only, never a filesystem path.
 */
internal object ApkDisplayName {
    private const val FALLBACK = "selected.apk"
    private const val MAX_LENGTH = 120

    fun safe(raw: String?): String {
        if (raw.isNullOrBlank()) return FALLBACK
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val clean = buildString {
            for (char in leaf) {
                if (length >= MAX_LENGTH) break
                if (char.isISOControl() || char in '\u202A'..'\u202E' ||
                    char in '\u2066'..'\u2069' || char == '\u200E' || char == '\u200F') continue
                append(char)
            }
        }.trim()
        return clean.ifEmpty { FALLBACK }
    }
}
