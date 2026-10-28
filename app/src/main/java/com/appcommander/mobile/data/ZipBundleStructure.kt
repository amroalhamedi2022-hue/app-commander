package com.appcommander.mobile.data

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry

/**
 * Decode untrusted ZIP metadata as strict UTF-8. The default String constructor
 * silently replaces malformed sequences, so canonical metadata must fail closed.
 */
internal fun decodeStrictUtf8(bytes: ByteArray, description: String): String =
    decodeStrictUtf8(bytes, 0, bytes.size, description)

internal fun decodeStrictUtf8(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    description: String,
): String {
    if (offset < 0 || length < 0 || offset > bytes.size - length) {
        throw IOException("Invalid UTF-8 byte range for $description")
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, length))
            .toString()
    } catch (failure: CharacterCodingException) {
        throw IOException("Invalid UTF-8 in $description", failure)
    }
}

/**
 * Bounded, seek-free verification of the central directory for App Commander
 * bundles. ZipInputStream validates local entries but never validates the ZIP
 * central directory. This checker compares both inventories without writing a
 * potentially 2 GiB SAF document to private storage.
 *
 * Deliberately accepts only ordinary single-disk ZIP32 archives produced by
 * our exporter (no ZIP64, comments, encryption, or prepended executable data).
 * It does not authenticate an APK signer or validate the archive's provenance.
 */
internal object ZipBundleStructure {
    private const val TAIL_BYTES = 128 * 1024
    private const val MAX_RAW_BYTES = InstalledApkBundleExport.MAX_TOTAL_BYTES + 1024 * 1024L
    private const val EXTENDED_TIMESTAMP_ID = 0x5455
    private const val ZIP_VERSION_20 = 20

    /**
     * The exporter writes ZipEntry.time = 0 for every entry. On the supported
     * JDK/Android ZIP writer this requires exactly one UT extended timestamp
     * field: mtime flag only, epoch seconds zero. This verifier accepts only
     * that canonical representation, not arbitrary opaque extra fields that
     * different ZIP readers might interpret differently.
     */
    private fun validateCanonicalTimestampExtra(extra: ByteArray, location: String) {
        if (extra.size != 9 ||
            u16(extra, 0) != EXTENDED_TIMESTAMP_ID ||
            u16(extra, 2) != 5 ||
            (extra[4].toInt() and 0xff) != 1 ||
            u32(extra, 5) != 0L
        ) throw IOException("Unsupported ZIP $location extra fields")
    }

    data class LocalEntry(
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val crc: Long,
        val extra: ByteArray,
        val flags: Int,
        val method: Int,
        val versionNeeded: Int,
        val rawName: ByteArray,
        val offset: Long,
        val headerLength: Int,
    )

    /**
     * Capture the raw local header by its logical archive offset. ZipInputStream
     * may read ahead into its inflater buffer, so the physical stream counter is
     * not the same as the current logical entry position. The bounded tail keeps
     * the header available without retaining APK payloads.
     */
    fun captureLocalEntry(tracking: TrackingStream, offset: Long, entry: ZipEntry): LocalEntry {
        val fixed = tracking.bytesAt(offset, 30)
        if (u32(fixed, 0) != 0x04034b50L) throw IOException("Invalid ZIP local entry header")
        val versionNeeded = u16(fixed, 4)
        val flags = u16(fixed, 6)
        val method = u16(fixed, 8)
        if (versionNeeded != ZIP_VERSION_20) {
            throw IOException("Unsupported ZIP local version-needed value")
        }
        val nameLength = u16(fixed, 26)
        val extraLength = u16(fixed, 28)
        if (nameLength !in 1..64 || extraLength > 64) {
            throw IOException("Unsupported ZIP local header metadata")
        }
        val headerLength = 30 + nameLength + extraLength
        val header = tracking.bytesAt(offset, headerLength)
        val rawName = header.copyOfRange(30, 30 + nameLength)
        val name = decodeStrictUtf8(rawName, "ZIP local entry name")
        val extra = header.copyOfRange(30 + nameLength, headerLength)
        validateCanonicalTimestampExtra(extra, "local")
        if (name != entry.name || method != entry.method ||
            !extra.contentEquals(entry.extra ?: ByteArray(0))
        ) throw IOException("ZIP local header differs from parsed entry metadata")
        return LocalEntry(
            name = name,
            size = entry.size,
            compressedSize = entry.compressedSize,
            crc = entry.crc,
            extra = extra,
            flags = flags,
            method = method,
            versionNeeded = versionNeeded,
            rawName = rawName,
            offset = offset,
            headerLength = headerLength,
        )
    }

    /** Captures only the final 128 KiB, including any bytes read ahead by ZipInputStream. */
    class TrackingStream(
        private val source: InputStream,
        private val checkCancelled: () -> Unit = {},
    ) : InputStream() {
        private val tail = ByteArray(TAIL_BYTES)
        private var next = 0
        private var filled = 0
        private var consecutiveZeroReads = 0
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
            checkCancelled()
            val value = source.read()
            consecutiveZeroReads = 0
            if (value >= 0) append(value)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            // InputStream must make progress for a positive length, but some
            // SAF providers violate that contract. ZipInputStream's header
            // reader can otherwise spin forever before entry-level bounds run.
            if (length == 0) return 0
            checkCancelled()
            val count = source.read(buffer, offset, length)
            if (count == 0) {
                if (++consecutiveZeroReads >= 8) {
                    throw IOException("APK bundle stream stalled")
                }
                return 0
            }
            consecutiveZeroReads = 0
            if (count > 0) {
                if (bytesRead > MAX_RAW_BYTES - count) throw IOException("APK bundle ZIP exceeds safe raw size")
                for (index in offset until offset + count) appendByte(buffer[index])
                bytesRead += count
            }
            return count
        }

        private fun append(value: Int) {
            if (bytesRead >= MAX_RAW_BYTES) throw IOException("APK bundle ZIP exceeds safe raw size")
            appendByte(value.toByte())
            bytesRead++
        }

        private fun appendByte(value: Byte) {
            tail[next] = value
            next = (next + 1) % tail.size
            if (filled < tail.size) filled++
        }

        /** ZipInputStream stops at the central directory; consume the rest to validate it. */
        fun drain() {
            val buffer = ByteArray(8192)
            while (true) {
                val count = read(buffer)
                if (count < 0) return
                // TrackingStream owns the repeated-zero-read limit so both
                // ZipInputStream parsing and the trailing central-directory
                // drain fail closed with the same bounded behavior.
            }
        }

        fun bytesAt(offset: Long, length: Int): ByteArray {
            if (offset < 0 || length < 0 || length.toLong() > bytesRead ||
                offset > bytesRead - length
            ) throw IOException("ZIP local header lies outside captured input")
            val oldestOffset = bytesRead - filled
            if (offset < oldestOffset || offset + length > bytesRead) {
                throw IOException("ZIP local header is outside the bounded capture window")
            }
            val oldestIndex = (next - filled + tail.size) % tail.size
            val relative = (offset - oldestOffset).toInt()
            return ByteArray(length) { index ->
                tail[(oldestIndex + relative + index) % tail.size]
            }
        }

        fun finalBytes(): ByteArray = ByteArray(filled) { index ->
            tail[(next - filled + index + tail.size) % tail.size]
        }

        override fun close() = source.close()
    }

    @Throws(IOException::class)
    fun validate(rawTail: ByteArray, rawSize: Long, local: List<LocalEntry>) {
        // Our exporter writes no ZIP comment. The EOCD must be the final 22 bytes.
        if (rawTail.size < 22 || rawSize < 22) throw IOException("Missing ZIP central directory")
        val end = rawTail.size - 22
        if (u32(rawTail, end) != 0x06054b50L ||
            u16(rawTail, end + 4) != 0 || u16(rawTail, end + 6) != 0 ||
            u16(rawTail, end + 20) != 0
        ) throw IOException("Invalid ZIP end record")
        val onDisk = u16(rawTail, end + 8)
        val entries = u16(rawTail, end + 10)
        if (onDisk != local.size || entries != local.size || entries == 0 ||
            entries > InstalledApkBundleExport.MAX_APK_COUNT + 1
        ) throw IOException("ZIP central directory entry count mismatch")
        val centralSize = u32(rawTail, end + 12)
        val centralOffset = u32(rawTail, end + 16)
        val centralEnd = rawSize - 22
        if (centralOffset >= centralEnd || centralSize != centralEnd - centralOffset ||
            centralSize > rawTail.size - 22
        ) throw IOException("ZIP central directory bounds mismatch")
        var cursor = end - centralSize.toInt()
        // Our exporter writes each DEFLATED entry as:
        // 30-byte local header + UTF-8 name + timestamp extra + compressed
        // payload + 16-byte signed data descriptor. Both local and central
        // timestamp extra lengths are identical for these generated entries.
        // Checking only increasing offsets allowed a forged central index to
        // point into compressed data while all APK digests still matched.
        var expectedLocalOffset = 0L
        for (expected in local) {
            if (cursor > end - 46 || u32(rawTail, cursor) != 0x02014b50L) {
                throw IOException("Invalid ZIP central directory entry")
            }
            val versionMadeBy = u16(rawTail, cursor + 4)
            val creatorVersion = versionMadeBy and 0xff
            val hostSystem = versionMadeBy ushr 8
            val versionNeeded = u16(rawTail, cursor + 6)
            val externalAttributes = u32(rawTail, cursor + 38)
            val flags = u16(rawTail, cursor + 8)
            val method = u16(rawTail, cursor + 10)
            val crc = u32(rawTail, cursor + 16)
            val compressed = u32(rawTail, cursor + 20)
            val size = u32(rawTail, cursor + 24)
            val nameLength = u16(rawTail, cursor + 28)
            val extraLength = u16(rawTail, cursor + 30)
            val commentLength = u16(rawTail, cursor + 32)
            val startDisk = u16(rawTail, cursor + 34)
            val localOffset = u32(rawTail, cursor + 42)
            val nextCursor = cursor.toLong() + 46 + nameLength + extraLength + commentLength
            if (nextCursor > end || creatorVersion != ZIP_VERSION_20 ||
                versionNeeded != ZIP_VERSION_20 || versionNeeded != expected.versionNeeded ||
                (flags != 8 && flags != 0x808) || method != 8 ||
                startDisk != 0 || commentLength != 0 ||
                localOffset != expectedLocalOffset || localOffset >= centralOffset ||
                nameLength !in 1..64 || extraLength > 64 ||
                size == 0xffffffffL || compressed == 0xffffffffL ||
                // The exporter uses DEFLATE level zero for every entry.
                // A smaller compressed payload cannot be one of our
                // canonical backups, even if its SHA manifest is valid.
                compressed < size
            ) throw IOException("Unsupported ZIP central directory metadata")
            if (flags != expected.flags || method != expected.method ||
                localOffset != expected.offset
            ) throw IOException("ZIP local/central header metadata mismatch")
            val centralNameBytes = rawTail.copyOfRange(cursor + 46, cursor + 46 + nameLength)
            val name = decodeStrictUtf8(centralNameBytes, "ZIP entry name")
            ZipEntryTypeSafety.validate(hostSystem, externalAttributes, name)
            if (!centralNameBytes.contentEquals(expected.rawName)) {
                throw IOException("ZIP local/central entry name bytes differ")
            }
            val extraStart = cursor + 46 + nameLength
            val centralExtra = rawTail.copyOfRange(extraStart, extraStart + extraLength)
            if (!centralExtra.contentEquals(expected.extra)) {
                throw IOException("ZIP central directory extra fields differ from local entries")
            }
            validateCanonicalTimestampExtra(centralExtra, "central")
            if (name != expected.name || size != expected.size ||
                compressed != expected.compressedSize || crc != expected.crc
            ) throw IOException("ZIP central directory differs from local entries")
            expectedLocalOffset += 30L + nameLength + extraLength + compressed + 16L
            if (expectedLocalOffset > centralOffset) {
                throw IOException("ZIP local entry extends into central directory")
            }
            cursor = nextCursor.toInt()
        }
        if (cursor != end || expectedLocalOffset != centralOffset) {
            throw IOException("ZIP local/central directory layout mismatch")
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset > bytes.size - 2) throw IOException("Truncated ZIP structure")
        return (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    }

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16)) and 0xffffffffL
}
