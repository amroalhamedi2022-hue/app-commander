package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.zip.CRC32
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
            val filePaths = HashSet<String>()
            // Includes explicit directories and implicit parents of entries.
            // A ZIP file must never masquerade as another entry's directory.
            val directoryPaths = HashSet<String>()
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
                val segments = path.split('/')
                var parent = ""
                for (i in 0 until segments.lastIndex) {
                    parent = if (i == 0) segments[i] else "$" + "{parent}/$" + "{segments[i]}"
                    if (parent in filePaths) {
                        throw IOException("APK ZIP entry is nested under a file")
                    }
                    directoryPaths.add(parent)
                }
                if (entry.isDirectory) {
                    if (path in filePaths) throw IOException("APK ZIP file/directory collision")
                    directoryPaths.add(path)
                } else {
                    if (path in directoryPaths) throw IOException("APK ZIP file/directory collision")
                    filePaths.add(path)
                }
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
            // Unlike merely trusting the central directory, read ONLY the
            // bounded manifest and verify its actual byte count and CRC. This
            // catches truncated/corrupt manifest payloads without extracting
            // APK entries or inflating untrusted DEX/resources. CRC is NOT a
            // signature or an authenticity check.
            val manifest = zip.getEntry("AndroidManifest.xml")
                ?: throw IOException("APK manifest entry disappeared")
            val crc = CRC32()
            val buffer = ByteArray(16 * 1024)
            var readBytes = 0L
            zip.getInputStream(manifest).use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) throw IOException("APK manifest stream stalled")
                    readBytes += n
                    if (readBytes > MAX_MANIFEST_BYTES) {
                        throw IOException("APK manifest exceeds inspection limits")
                    }
                    crc.update(buffer, 0, n)
                }
            }
            if (readBytes != manifest.size || crc.value != manifest.crc) {
                throw IOException("APK manifest payload does not match ZIP metadata")
            }
            return Summary(count, expandedBytes)
        }
    }
}
