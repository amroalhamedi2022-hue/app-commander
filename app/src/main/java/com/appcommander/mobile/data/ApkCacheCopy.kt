package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Bounded streaming copy for APK inspection; does not trust provider metadata. */
internal object ApkCacheCopy {
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    private const val MAX_EMPTY_READS = 8

    data class CopyResult(val sizeBytes: Long, val sha256: String)

    /** Always remove a temporary APK, even if the provider or parser throws. */
    fun <T> withTemporaryApk(cacheDir: File, inspect: (File) -> T): T {
        val cached = File.createTempFile("inspect-", ".apk", cacheDir)
        try {
            return inspect(cached)
        } finally {
            cached.delete()
        }
    }

    /** Copy and hash in a single streaming pass, avoiding a second 512 MiB disk read. */
    fun copyWithSha256(input: InputStream, output: OutputStream, maxBytes: Long = MAX_APK_BYTES): CopyResult {
        require(maxBytes > 0) { "APK copy limit must be positive" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var emptyReads = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                if (++emptyReads >= MAX_EMPTY_READS) {
                    throw IOException("APK source stopped returning data")
                }
                continue
            }
            emptyReads = 0
            if (count.toLong() > maxBytes - total) {
                throw IOException("APK exceeds inspection limit ($maxBytes bytes)")
            }
            // Only hash bytes successfully written to the cache. If the write
            // fails, inspection aborts and the partial file is removed.
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
            total += count
        }
        if (total == 0L) throw IOException("APK file is empty")
        return CopyResult(total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    /** Compatibility entry point for callers that only need the byte count. */
    fun copy(input: InputStream, output: OutputStream, maxBytes: Long = MAX_APK_BYTES): Long =
        copyWithSha256(input, output, maxBytes).sizeBytes
}
