package com.appcommander.mobile.data

/**
 * SAF display names are untrusted provider labels, not filesystem paths.
 * Strip invisible formatting and bidi spoofing code points while preserving
 * visible Arabic and supplementary Unicode characters (including emoji).
 */
internal object ApkDisplayName {
    private const val FALLBACK = "selected.apk"
    private const val MAX_LENGTH = 120

    private fun invisibleOrUnsafe(code: Int): Boolean =
        Character.isISOControl(code) || Character.getType(code) == Character.FORMAT.toInt() ||
            code in 0xD800..0xDFFF || code == 0x00AD || code == 0x061C ||
            code in 0x200B..0x200F || code in 0x202A..0x202E ||
            code in 0x2060..0x2069 || code == 0xFEFF

    fun safe(raw: String?): String {
        if (raw.isNullOrBlank()) return FALLBACK
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val clean = StringBuilder()
        var index = 0
        while (index < leaf.length) {
            val code = Character.codePointAt(leaf, index)
            index += Character.charCount(code)
            if (invisibleOrUnsafe(code)) continue
            val chars = Character.toChars(code)
            if (clean.length + chars.size > MAX_LENGTH) break
            clean.append(chars)
        }
        val label = clean.toString().trim()
        return label.takeUnless { it.isEmpty() || it == "." || it == ".." } ?: FALLBACK
    }
}
