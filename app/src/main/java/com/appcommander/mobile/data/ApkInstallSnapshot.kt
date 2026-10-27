package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Freeze a SAF APK into an app-private cache file before handing it to Android's
 * installer. The installer receives our FileProvider URI, never the mutable
 * third-party SAF URI. This closes the provider-swap window after rehashing.
 *
 * This does NOT authenticate the APK signature, replace Android's package
 * verifier, or guarantee that installation will succeed.
 */
internal object ApkInstallSnapshot {
    const val DIRECTORY = "verified-apk-installs"
    const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    private const val PREFIX = "verified-"
    private const val SUFFIX = ".apk"
    private val digestPattern = Regex("[0-9a-fA-F]{64}")

    /**
     * The caller owns the returned file and exposes it only through a
     * read-only FileProvider. On mismatch or I/O failure, delete all partial
     * bytes; never return a URI for an unverified file.
     */
    @Throws(IOException::class)
    fun create(
        directory: File,
        input: InputStream,
        expectedSize: Long,
        expectedSha256: String,
    ): File? {
        if (expectedSize !in 1..ApkCacheCopy.MAX_APK_BYTES ||
            !digestPattern.matches(expectedSha256)) return null
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create private install snapshot directory")
        }
        // Expired verified APKs can consume hundreds of MiB each. Reclaim
        // them BEFORE allocating a new snapshot, otherwise a full cache may
        // prevent installation before the post-copy cleanup ever runs.
        cleanupExpired(directory)
        val file = File.createTempFile(PREFIX, SUFFIX, directory)
        var verified = false
        try {
            val copied = file.outputStream().use { output ->
                // Stop immediately if the provider streams more than the
                // previously inspected length; no unbounded disk writes.
                ApkCacheCopy.copyWithSha256(input, output, expectedSize)
            }
            verified = copied.sizeBytes == expectedSize &&
                copied.sha256.equals(expectedSha256, ignoreCase = true)
            return if (verified) file else null
        } finally {
            if (!verified) file.delete()
        }
    }

    /** Remove only our own expired snapshots, never arbitrary cache files. */
    fun cleanupExpired(
        directory: File,
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeMillis: Long = MAX_AGE_MILLIS,
    ): Int {
        require(maxAgeMillis > 0)
        // A pre-epoch clock can underflow here and wrap the cutoff into the
        // future, deleting valid snapshots. Cache cleanup is best-effort:
        // when wall-clock age cannot be represented safely, fail closed.
        if (nowMillis < 0L || nowMillis < maxAgeMillis) return 0
        val cutoff = nowMillis - maxAgeMillis

        // Cache housekeeping must never abort a verified install merely because
        // the filesystem denies metadata access. A zero mtime is ambiguous
        // (epoch timestamp or unavailable metadata), so preserve it as unknown.
        val isDirectory = try {
            directory.isDirectory
        } catch (_: SecurityException) {
            return 0
        }
        if (!isDirectory) return 0
        val files = try {
            directory.listFiles().orEmpty()
        } catch (_: SecurityException) {
            return 0
        }
        return files.count { file ->
            try {
                if (!file.isFile || !file.name.startsWith(PREFIX) || !file.name.endsWith(SUFFIX)) {
                    false
                } else {
                    val modifiedAt = file.lastModified()
                    modifiedAt > 0L && modifiedAt < cutoff && file.delete()
                }
            } catch (_: SecurityException) {
                // Skip only the inaccessible entry; continue cleaning others.
                false
            }
        }
    }
}
