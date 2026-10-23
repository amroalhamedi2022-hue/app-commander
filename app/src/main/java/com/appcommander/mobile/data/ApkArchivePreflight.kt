package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Cheap, bounded ZIP central-directory checks before handing untrusted SAF
 * bytes to Android's PackageManager. No entries are extracted or inflated.
 *
 * This is NOT an APK signature check, malware scan, or proof that ZIP entry
 * payloads match their CRCs or local ZIP headers. Platform parsing remains
 * authoritative, and the user must still review the inspected application.
 */
internal object ApkArchivePreflight {
    const val MAX_ENTRIES = 30_000
    const val MAX_ENTRY_NAME_LENGTH = 1_024
    const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024
    const val MAX_DECLARED_UNCOMPRESSED_BYTES = 4L * 1024 * 1024 * 1024

    data class Summary(val entries: Int, val declaredUncompressedBytes: Long)

    @Throws(IOException::class)
    fun check(file: File): Summary {
        if (!file.isFile || file.length() == 0L) throw IOException("APK archive is empty")
        ZipFile(file).use { zip ->
            val names = HashSet<String>()
            val entries = zip.entries()
            var count = 0
            var expandedBytes = 0L
            var manifestFound = false
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > MAX_ENTRIES) throw IOException("APK has too many ZIP entries")
                val name = entry.name
                // Directory entries legitimately end in '/', but an empty
                // segment *inside* a path is ambiguous across ZIP readers.
                val path = if (entry.isDirectory) name.dropLast(1) else name
                if (name.length > MAX_ENTRY_NAME_LENGTH ||
                    path.isEmpty() || name.startsWith('/') || name.contains('\\') ||
                    name.any { it.code < 32 || it.code == 127 } ||
                    path.split('/').any { it.isEmpty() || it == "." || it == ".." }
                ) throw IOException("APK has an unsafe ZIP entry path")
                if (!names.add(name)) throw IOException("APK has duplicate ZIP entry names")
                val size = entry.size
                if (size < 0L || size > MAX_DECLARED_UNCOMPRESSED_BYTES - expandedBytes) {
                    throw IOException("APK ZIP metadata exceeds inspection limits")
                }
                expandedBytes += size
                if (name == "AndroidManifest.xml" && !entry.isDirectory) {
                    if (size == 0L || size > MAX_MANIFEST_BYTES) {
                        throw IOException("APK manifest size exceeds inspection limits")
                    }
                    manifestFound = true
                }
            }
            if (!manifestFound) throw IOException("APK is missing a nonempty AndroidManifest.xml")
            return Summary(count, expandedBytes)
        }
    }
}
