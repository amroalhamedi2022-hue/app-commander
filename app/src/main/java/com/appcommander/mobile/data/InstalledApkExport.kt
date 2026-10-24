package com.appcommander.mobile.data

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * SAF export of an installed app's BASE APK only. No root, Shizuku or
 * Device Owner is needed. Split APKs are deliberately not represented as
 * a complete reinstallable backup by this operation.
 */
internal object InstalledApkExport {
    data class Result(val bytes: Long, val sha256: String, val hasSplits: Boolean)

    /**
     * ACTION_CREATE_DOCUMENT creates the destination before copying begins.
     * If any step fails, make a best-effort attempt to remove that newly
     * created document rather than silently leaving a corrupt APK behind.
     * Cleanup failures must never hide the original copy failure.
     *
     * The caller MUST supply a cleanup callback scoped to the newly created
     * SAF document, never an existing user-selected document.
     */
    inline fun <T> withPartialDocumentCleanup(
        cleanup: () -> Unit,
        write: () -> T,
    ): T {
        try {
            return write()
        } catch (failure: Throwable) {
            try {
                cleanup()
            } catch (_: Throwable) {
                // Preserve the original failure and allow UI to warn the user.
            }
            throw failure
        }
    }

    @Throws(IOException::class)
    fun copyBase(
        input: InputStream,
        output: OutputStream,
        expectedBytes: Long,
        hasSplits: Boolean,
    ): Result {
        if (expectedBytes !in 1..ApkCacheCopy.MAX_APK_BYTES) {
            throw IOException("Installed base APK exceeds safe export limits")
        }
        val copied = ApkCacheCopy.copyWithSha256(input, output, expectedBytes)
        if (copied.sizeBytes != expectedBytes) {
            throw IOException("Installed base APK changed or was truncated during export")
        }
        return Result(copied.sizeBytes, copied.sha256, hasSplits)
    }
}
