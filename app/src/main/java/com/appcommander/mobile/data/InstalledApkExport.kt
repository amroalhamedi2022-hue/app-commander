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

    /**
     * Complete the single-base SAF export only after the newly created document
     * has been closed, reopened and independently hashed. A provider can accept
     * every write but persist different/truncated bytes. This detects that case
     * without retaining the APK in memory or making a second private copy.
     *
     * The caller must use withPartialDocumentCleanup around this operation,
     * scoped ONLY to the fresh CreateDocument URI. A readback failure is fatal.
     * This verifies saved bytes, NOT the APK signature or future provider changes.
     */
    @Throws(IOException::class)
    fun copyBaseAndVerify(
        openSource: () -> InputStream,
        openForWrite: () -> OutputStream,
        openForRead: () -> InputStream,
        expectedBytes: Long,
        hasSplits: Boolean,
    ): Result {
        // Reject invalid sizes before opening or creating any output stream.
        if (expectedBytes !in 1..ApkCacheCopy.MAX_APK_BYTES) {
            throw IOException("Installed base APK exceeds safe export limits")
        }
        val written = openSource().use { input ->
            openForWrite().use { output ->
                copyBase(input, output, expectedBytes, hasSplits)
            }
        }
        // Both streams are closed before readback: remote SAF providers may
        // commit their data only on close().
        val matches = openForRead().use { input ->
            ApkInstallIntegrity.matches(input, written.bytes, written.sha256)
        }
        if (!matches) throw IOException("Saved SAF base APK failed SHA-256 readback")
        return written
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
