package com.appcommander.mobile.data

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Bounded streaming copy for APK inspection; does not trust provider metadata. */
internal object ApkCacheCopy {
    const val MAX_APK_BYTES = 512L * 1024 * 1024
    private const val MAX_EMPTY_READS = 8

    fun copy(input: InputStream, output: OutputStream, maxBytes: Long = MAX_APK_BYTES): Long {
        require(maxBytes > 0) { "APK copy limit must be positive" }
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
            output.write(buffer, 0, count)
            total += count
        }
        if (total == 0L) throw IOException("APK file is empty")
        return total
    }
}
