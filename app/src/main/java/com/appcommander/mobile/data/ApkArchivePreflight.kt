package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.text.Normalizer
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
    const val MAX_CENTRAL_DIRECTORY_BYTES = 32L * 1024 * 1024

    data class Summary(val entries: Int, val declaredUncompressedBytes: Long)

    /** Reject invisible direction overrides and controls that can disguise ZIP paths in UI. */
    private fun unsafeDisplayCodePoint(code: Int): Boolean =
        code < 32 || code in 0x7f..0x9f || code == 0x00ad || code == 0x061c ||
            code in 0x200b..0x200f || code in 0x202a..0x202e ||
            code in 0x2060..0x2069 || code == 0xfeff

    @Throws(IOException::class)
    fun check(file: File): Summary {
        if (!file.isFile) throw IOException("APK archive is missing")
        val fileLength = file.length()
        if (fileLength == 0L) throw IOException("APK archive is empty")
        if (fileLength > ApkCacheCopy.MAX_APK_BYTES) {
            throw IOException("APK archive exceeds safe inspection size")
        }
        // Bound ZIP metadata BEFORE java.util.zip.ZipFile allocates an entry
        // table from the untrusted central directory.
        preflightCentralDirectory(file)
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
                // Distinct ZIP spellings may render as the same path in a UI
                // or filesystem (e.g. precomposed vs decomposed accents).
                // Normalize for collision checks, never rewrite the archive.
                val canonicalName = Normalizer.normalize(name, Normalizer.Form.NFC)
                // Directory entries legitimately end in '/', but an empty
                // segment *inside* a path is ambiguous across ZIP readers.
                val path = if (entry.isDirectory) canonicalName.dropLast(1) else canonicalName
                if (name.length > MAX_ENTRY_NAME_LENGTH ||
                    path.isEmpty() || name.startsWith('/') || name.contains('\\') ||
                    name.any { unsafeDisplayCodePoint(it.code) } ||
                    path.split('/').any { it.isEmpty() || it == "." || it == ".." }
                ) throw IOException("APK has an unsafe ZIP entry path")
                if (!names.add(canonicalName)) throw IOException("APK has duplicate or canonically equivalent ZIP entry names")
                val segments = path.split('/')
                var parent = ""
                for (i in 0 until segments.lastIndex) {
                    parent = if (i == 0) segments[i] else "${parent}/${segments[i]}"
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
                val compressedSize = entry.compressedSize
                // Never trust central-directory lengths blindly. Reject
                // unsupported compression, impossible sizes and nonempty
                // directory payloads before invoking Android's APK parser.
                // This is metadata validation, NOT verification of all ZIP
                // local headers, CRCs or APK signatures.
                if (entry.method != java.util.zip.ZipEntry.STORED &&
                    entry.method != java.util.zip.ZipEntry.DEFLATED) {
                    throw IOException("APK contains an unsupported ZIP compression method")
                }
                if (compressedSize < 0L || compressedSize > file.length() ||
                    (size > 0L && compressedSize == 0L) ||
                    (entry.isDirectory && size != 0L)) {
                    throw IOException("APK contains inconsistent ZIP entry metadata")
                }
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

    /**
     * Read only the final ZIP32 EOCD window before opening ZipFile. This
     * prevents an attacker-controlled entry count or central-directory blob
     * from causing large allocations before MAX_ENTRIES/path limits run.
     * APK inspection is bounded to 512 MiB, so ZIP64 and multi-disk archives
     * are intentionally rejected rather than partially interpreted.
     */
    private fun preflightCentralDirectory(file: File) {
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val maxTail = 22L + 0xffffL
            val tailLength = minOf(length, maxTail).toInt()
            if (tailLength < 22) throw IOException("APK ZIP end record is missing")
            val tail = ByteArray(tailLength)
            input.seek(length - tailLength)
            input.readFully(tail)

            var endRecord = -1
            for (offset in tail.size - 22 downTo 0) {
                if (readU32(tail, offset) != 0x06054b50L) continue
                val commentLength = readU16(tail, offset + 20)
                if (offset + 22 + commentLength == tail.size) {
                    endRecord = offset
                    break
                }
            }
            if (endRecord < 0) throw IOException("APK ZIP end record is missing or malformed")

            val disk = readU16(tail, endRecord + 4)
            val directoryDisk = readU16(tail, endRecord + 6)
            val diskEntries = readU16(tail, endRecord + 8)
            val totalEntries = readU16(tail, endRecord + 10)
            val directoryBytes = readU32(tail, endRecord + 12)
            val directoryOffset = readU32(tail, endRecord + 16)
            val absoluteEndRecord = length - tailLength + endRecord

            if (disk != 0 || directoryDisk != 0) {
                throw IOException("Multi-disk APK ZIP is unsupported")
            }
            if (diskEntries == 0xffff || totalEntries == 0xffff ||
                directoryBytes == 0xffffffffL || directoryOffset == 0xffffffffL
            ) throw IOException("ZIP64 APK archives are unsupported")
            if (diskEntries != totalEntries) throw IOException("APK ZIP entry count mismatch")
            if (totalEntries > MAX_ENTRIES) throw IOException("APK has too many ZIP entries")
            if (totalEntries == 0) throw IOException("APK ZIP contains no entries")
            if (directoryBytes > MAX_CENTRAL_DIRECTORY_BYTES) {
                throw IOException("APK central directory exceeds inspection limits")
            }
            if (directoryOffset + directoryBytes != absoluteEndRecord ||
                directoryOffset > absoluteEndRecord || directoryBytes > absoluteEndRecord
            ) throw IOException("APK ZIP central directory bounds are invalid")
        }
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset > bytes.size - 2) throw IOException("Truncated APK ZIP end record")
        return (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        readU16(bytes, offset).toLong() or (readU16(bytes, offset + 2).toLong() shl 16)
}
