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
