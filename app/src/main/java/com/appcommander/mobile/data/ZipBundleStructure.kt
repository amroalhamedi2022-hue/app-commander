package com.appcommander.mobile.data

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

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

    data class LocalEntry(val name: String, val size: Long, val compressedSize: Long, val crc: Long)

    /** Captures only the final 128 KiB, including any bytes read ahead by ZipInputStream. */
    class TrackingStream(private val source: InputStream) : InputStream() {
        private val tail = ByteArray(TAIL_BYTES)
        private var next = 0
        private var filled = 0
        private var consecutiveZeroReads = 0
        var bytesRead: Long = 0
            private set

        override fun read(): Int {
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
            if (nextCursor > end || (flags != 8 && flags != 0x808) ||
                method != 8 || startDisk != 0 || commentLength != 0 ||
                localOffset != expectedLocalOffset || localOffset >= centralOffset ||
                nameLength !in 1..64 || extraLength > 64 ||
                size == 0xffffffffL || compressed == 0xffffffffL ||
                // The exporter uses DEFLATE level zero for every entry.
                // A smaller compressed payload cannot be one of our
                // canonical backups, even if its SHA manifest is valid.
                compressed < size
            ) throw IOException("Unsupported ZIP central directory metadata")
            val name = decodeStrictUtf8(rawTail, cursor + 46, nameLength, "ZIP entry name")
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
