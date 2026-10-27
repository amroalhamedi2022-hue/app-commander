package com.appcommander.mobile.data

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

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

    fun matches(
        input: InputStream,
        expectedSize: Long,
        expectedSha256: String,
        checkCancelled: () -> Unit = {},
    ): Boolean {
        checkCancelled()
        if (expectedSize !in 1..ApkCacheCopy.MAX_APK_BYTES ||
            !sha256Pattern.matches(expectedSha256)) return false
        return try {
            val actual = ApkCacheCopy.copyWithSha256(input, discard, checkCancelled = checkCancelled)
            actual.sizeBytes == expectedSize &&
                actual.sha256.equals(expectedSha256, ignoreCase = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            false
        }
    }
}
