package com.appcommander.mobile.data

import java.io.InputStream
import java.io.OutputStream

/**
 * Recheck the original SAF stream before handing its URI to Android's installer.
 * A provider may replace the bytes after inspection. This detects changes up
 * to the second read, but cannot make a third-party mutable URI atomic: the
 * installer remains the final authority and may reopen it later.
 */
internal object ApkInstallIntegrity {
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    private val discard = object : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    fun matches(input: InputStream, expectedSize: Long, expectedSha256: String): Boolean {
        if (expectedSize !in 1..ApkCacheCopy.MAX_APK_BYTES ||
            !sha256Pattern.matches(expectedSha256)) return false
        // An empty, stalled or failing provider is an integrity mismatch,
        // not a recoverable reason to open the installer.
        return runCatching {
            val actual = ApkCacheCopy.copyWithSha256(input, discard)
            actual.sizeBytes == expectedSize &&
                actual.sha256.equals(expectedSha256, ignoreCase = true)
        }.getOrDefault(false)
    }
}
