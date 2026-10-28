package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Cheap, bounded ZIP structure checks before handing untrusted SAF bytes to
 * Android's PackageManager. Central and local metadata are cross-checked;
 * no entries are extracted or inflated except the bounded manifest read.
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

    // APK inspection supports only ZIP32 STORED/DEFLATED entries, standard
    // data descriptors, UTF-8 names, and DEFLATE option bits. Unknown/reserved
    // flags are rejected to avoid platform-parser interpretation differences.
    private const val ALLOWED_GENERAL_PURPOSE_FLAGS = 0x080e
    private const val DEFLATE_OPTION_FLAGS = 0x0006
    private val APK_SIGNING_BLOCK_MAGIC = "APK Sig Block 42".toByteArray(StandardCharsets.US_ASCII)
    private const val MAX_APK_SIGNING_BLOCK_BYTES = 64L * 1024 * 1024
    private const val MAX_APK_SIGNING_BLOCK_PAIRS = 1_024

    data class Summary(val entries: Int, val declaredUncompressedBytes: Long)

    private class PathRegistry {
        val names = HashSet<String>()
        val filePaths = HashSet<String>()
        // Includes explicit directories and implicit parents of entries.
        val directoryPaths = HashSet<String>()
    }

    /** Reject invisible direction overrides and controls that can disguise ZIP paths in UI. */
    private fun unsafeDisplayCodePoint(code: Int): Boolean =
        code < 32 || code in 0x7f..0x9f || code == 0x00ad || code == 0x061c ||
            code in 0x200b..0x200f || code in 0x202a..0x202e ||
            code in 0x2060..0x2069 || code == 0xfeff

    /**
     * Apply path safety and collision rules to a bounded entry name. Called
     * during central-directory preflight and again after ZipFile enumeration.
     */
    private fun registerPath(
        name: String,
        isDirectory: Boolean,
        registry: PathRegistry,
        location: String,
    ) {
        val canonicalName = Normalizer.normalize(name, Normalizer.Form.NFC)
        val path = if (isDirectory) canonicalName.dropLast(1) else canonicalName
        if (name.length > MAX_ENTRY_NAME_LENGTH ||
            path.isEmpty() || name.startsWith('/') || name.contains('\\') ||
            name.any { unsafeDisplayCodePoint(it.code) } ||
            path.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) throw IOException("APK $location has an unsafe ZIP entry path")

        if (!registry.names.add(canonicalName)) {
            throw IOException("APK $location has duplicate or canonically equivalent ZIP entry names")
        }
        val segments = path.split('/')
        var parent = ""
        for (i in 0 until segments.lastIndex) {
            parent = if (i == 0) segments[i] else "${parent}/${segments[i]}"
            if (parent in registry.filePaths) {
                throw IOException("APK $location ZIP entry is nested under a file")
            }
            registry.directoryPaths.add(parent)
        }
        if (isDirectory) {
            if (path in registry.filePaths) {
                throw IOException("APK $location has a ZIP file/directory collision")
            }
            registry.directoryPaths.add(path)
        } else {
            if (path in registry.directoryPaths) {
                throw IOException("APK $location has a ZIP file/directory collision")
            }
            registry.filePaths.add(path)
        }
    }

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
            val paths = PathRegistry()
            val entries = zip.entries()
            var count = 0
            var expandedBytes = 0L
            var manifestFound = false
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > MAX_ENTRIES) throw IOException("APK has too many ZIP entries")
                val name = entry.name
                registerPath(name, entry.isDirectory, paths, "ZIP entry inventory")
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
            var matchingEndRecords = 0
            // A signature-shaped sequence can occur inside the arbitrary ZIP
            // comment. Count every candidate whose declared comment ends exactly
            // at EOF instead of blindly trusting the last matching signature;
            // ambiguous candidates can make preflight and ZipFile parse different
            // directory metadata, so reject before invoking the platform parser.
            for (offset in tail.size - 22 downTo 0) {
                if (readU32(tail, offset) != 0x06054b50L) continue
                val commentLength = readU16(tail, offset + 20)
                if (offset + 22 + commentLength == tail.size) {
                    matchingEndRecords++
                    if (endRecord < 0) endRecord = offset
                }
            }
            if (matchingEndRecords > 1) {
                throw IOException("APK ZIP end record is ambiguous")
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
            // Check every bounded central-directory record before ZipFile can
            // allocate its entry table. EOCD counts alone are untrusted and do
            // not prove that the declared number of records fits the directory.
            val localRanges = validateCentralDirectory(file, directoryOffset, directoryBytes, totalEntries)
            validateApkSigningBlock(file, directoryOffset, localRanges)
        }
    }

    private fun validateCentralDirectory(
        file: File,
        offset: Long,
        directoryBytes: Long,
        expectedEntries: Int,
    ): List<Pair<Long, Long>> {
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset)
            var consumed = 0L
            val localRanges = ArrayList<Pair<Long, Long>>(expectedEntries)
            var declaredExpandedBytes = 0L
            val paths = PathRegistry()
            val archiveLength = file.length()
            repeat(expectedEntries) {
                if (directoryBytes - consumed < 46L) {
                    throw IOException("Truncated APK ZIP central directory entry")
                }
                val header = ByteArray(46)
                input.readFully(header)
                if (readU32(header, 0) != 0x02014b50L) {
                    throw IOException("Invalid APK ZIP central directory signature")
                }
                val versionMadeBy = readU16(header, 4)
                val hostSystem = versionMadeBy ushr 8
                val externalAttributes = readU32(header, 38)
                val flags = readU16(header, 8)
                val method = readU16(header, 10)
                val crc = readU32(header, 16)
                val compressedSize = readU32(header, 20)
                val uncompressedSize = readU32(header, 24)
                val nameLength = readU16(header, 28)
                val extraLength = readU16(header, 30)
                val commentLength = readU16(header, 32)
                val startDisk = readU16(header, 34)
                val localHeaderOffset = readU32(header, 42)
                val variableBytes = nameLength.toLong() + extraLength + commentLength
                val recordBytes = 46L + variableBytes
                if (nameLength !in 1..MAX_ENTRY_NAME_LENGTH ||
                    startDisk != 0 ||
                    compressedSize == 0xffffffffL ||
                    uncompressedSize == 0xffffffffL ||
                    localHeaderOffset == 0xffffffffL ||
                    localHeaderOffset >= offset ||
                    recordBytes > directoryBytes - consumed
                ) throw IOException("Unsupported or inconsistent APK ZIP central metadata")
                validateGeneralPurposeFlags(flags, method, "central")
                // Reject semantically impossible entry metadata before ZipFile
                // parses/allocates its entry table. The archive and entry counts
                // are bounded, but declared expansion must be bounded here too.
                if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                    throw IOException("Unsupported APK ZIP compression method")
                }
                if (compressedSize > archiveLength ||
                    (uncompressedSize > 0L && compressedSize == 0L)
                ) throw IOException("APK ZIP entry has impossible compressed size metadata")
                if (method == ZipEntry.STORED && compressedSize != uncompressedSize) {
                    throw IOException("STORED ZIP entry sizes disagree")
                }
                if (uncompressedSize > MAX_DECLARED_UNCOMPRESSED_BYTES - declaredExpandedBytes) {
                    throw IOException("APK ZIP aggregate expansion exceeds inspection limits")
                }
                declaredExpandedBytes += uncompressedSize

                val nameBytes = ByteArray(nameLength)
                input.readFully(nameBytes)
                // Android's ZipFile decodes entry names as UTF-8. Validate the raw
                // bytes here so malformed sequences fail before platform parsing.
                validateUtf8Field(nameBytes, "entry name")
                val decodedName = String(nameBytes, StandardCharsets.UTF_8)
                validateExternalFileType(hostSystem, externalAttributes, decodedName)
                registerPath(
                    name = decodedName,
                    isDirectory = nameBytes.lastOrNull() == '/'.code.toByte(),
                    registry = paths,
                    location = "central directory",
                )
                val extraOffset = input.filePointer
                validateExtraFields(input, extraOffset, extraLength, "central")
                if (commentLength > 0) {
                    input.seek(extraOffset + extraLength)
                    val commentBytes = ByteArray(commentLength)
                    input.readFully(commentBytes)
                    validateUtf8Field(commentBytes, "entry comment")
                }
                if (nameBytes.lastOrNull() == '/'.code.toByte() && uncompressedSize != 0L) {
                    throw IOException("APK ZIP directory entry advertises uncompressed data")
                }
                input.seek(extraOffset + extraLength + commentLength)
                consumed += recordBytes

                val rangeEnd = validateLocalRecord(
                    input = input,
                    localOffset = localHeaderOffset,
                    centralOffset = offset,
                    centralName = nameBytes,
                    centralFlags = flags,
                    centralMethod = method,
                    centralCrc = crc,
                    centralCompressedSize = compressedSize,
                    centralUncompressedSize = uncompressedSize,
                )
                localRanges.add(localHeaderOffset to rangeEnd)
            }
            if (consumed != directoryBytes) {
                throw IOException("APK ZIP central directory record count/size mismatch")
            }

            // Distinct central-directory records must not alias or overlap the
            // same local header, compressed bytes, or optional data descriptor.
            val ordered = localRanges.sortedBy { it.first }
            for (index in 1 until ordered.size) {
                if (ordered[index].first < ordered[index - 1].second) {
                    throw IOException("APK ZIP local records overlap")
                }
            }
            return ordered
        }
    }

    /**
     * ZIP external attributes are host-system dependent. For Unix-created APKs,
     * inspect the POSIX type bits so symlinks/devices/FIFOs cannot be presented
     * as ordinary package entries, and require regular-file/directory metadata
     * to agree with the path form. Unknown/unspecified type bits remain allowed
     * for compatibility with ZIP writers that do not populate POSIX modes.
     *
     * This is structural preflight only; it is not APK signature verification.
     */
    private fun validateExternalFileType(hostSystem: Int, externalAttributes: Long, name: String) {
        ZipEntryTypeSafety.validate(hostSystem, externalAttributes, name)
    }

    /**
     * Validate the bounded APK Signing Block container when its magic is present.
     * This checks framing only; it does not verify signer certificates or digests.
     * V1-only APKs without the magic remain supported, and unknown pair IDs are opaque.
     */
    private fun validateApkSigningBlock(
        file: File,
        centralOffset: Long,
        localRanges: List<Pair<Long, Long>>,
    ) {
        if (centralOffset < APK_SIGNING_BLOCK_MAGIC.size) return
        RandomAccessFile(file, "r").use { input ->
            input.seek(centralOffset - APK_SIGNING_BLOCK_MAGIC.size)
            val magic = ByteArray(APK_SIGNING_BLOCK_MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(APK_SIGNING_BLOCK_MAGIC)) return
            if (centralOffset < 24L) {
                throw IOException("APK signing block footer is truncated")
            }

            input.seek(centralOffset - 24L)
            val footerSize = readU64(input)
            if (footerSize < 36L || footerSize > MAX_APK_SIGNING_BLOCK_BYTES - 8L) {
                throw IOException("APK signing block size is outside inspection limits")
            }
            val totalSize = footerSize + 8L
            if (totalSize > centralOffset || totalSize > MAX_APK_SIGNING_BLOCK_BYTES) {
                throw IOException("APK signing block bounds are invalid")
            }
            val blockStart = centralOffset - totalSize
            input.seek(blockStart)
            val headerSize = readU64(input)
            if (headerSize != footerSize) {
                throw IOException("APK signing block header/footer sizes disagree")
            }

            val pairEnd = centralOffset - 24L
            var cursor = blockStart + 8L
            var pairCount = 0
            while (cursor < pairEnd) {
                val remaining = pairEnd - cursor
                if (remaining < 12L) {
                    throw IOException("APK signing block contains a truncated ID/value pair")
                }
                input.seek(cursor)
                val pairSize = readU64(input)
                if (pairSize < 4L || pairSize > remaining - 8L) {
                    throw IOException("APK signing block ID/value pair length is invalid")
                }
                cursor += 8L + pairSize
                if (++pairCount > MAX_APK_SIGNING_BLOCK_PAIRS) {
                    throw IOException("APK signing block contains too many ID/value pairs")
                }
            }
            if (cursor != pairEnd || pairCount == 0) {
                throw IOException("APK signing block pair region is malformed")
            }
            if (localRanges.any { it.second > blockStart }) {
                throw IOException("APK signing block overlaps local ZIP entry data")
            }
        }
    }

    private fun readU64(input: RandomAccessFile): Long {
        var value = 0L
        repeat(8) { index ->
            value = value or (input.readUnsignedByte().toLong() shl (index * 8))
        }
        return value
    }

    /**
     * Reject ZIP general-purpose flags outside the deliberately supported APK
     * subset. Bits 1-2 describe compression options only for DEFLATED entries;
     * bit 3 is the optional data descriptor and bit 11 marks UTF-8 names.
     * Encryption/masked-header flags fail with a specific diagnostic.
     */
    private fun validateGeneralPurposeFlags(flags: Int, method: Int, headerKind: String) {
        if ((flags and 0x2041) != 0) {
            throw IOException("Encrypted APK ZIP entries are unsupported")
        }
        if ((flags and ALLOWED_GENERAL_PURPOSE_FLAGS.inv()) != 0) {
            throw IOException("Unsupported APK ZIP $headerKind general-purpose flags")
        }
        if (method != ZipEntry.DEFLATED && (flags and DEFLATE_OPTION_FLAGS) != 0) {
            throw IOException("APK ZIP compression-option flags require DEFLATED method")
        }
    }

    /**
     * Android's ZipFile decodes entry names and comments as UTF-8. Strictly
     * validate raw central-directory bytes before opening that parser so malformed
     * sequences cannot be silently replaced or interpreted differently.
     */
    private fun validateUtf8Field(bytes: ByteArray, fieldName: String) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
        } catch (error: CharacterCodingException) {
            throw IOException("APK ZIP $fieldName is not valid UTF-8", error)
        }
    }

    /**
     * Validate ZIP extra-field TLV framing without interpreting vendor payloads.
     * Each subrecord is a little-endian 2-byte ID, 2-byte data length, then data.
     * ZIP64 is rejected consistently with the ZIP32-only archive preflight.
     */
    private fun validateExtraFields(
        input: RandomAccessFile,
        offset: Long,
        length: Int,
        headerKind: String,
    ) {
        val archiveLength = input.length()
        if (length < 0 || offset < 0L || offset > archiveLength - length.toLong()) {
            throw IOException("APK ZIP $headerKind extra field is outside archive")
        }
        val originalPosition = input.filePointer
        try {
            input.seek(offset)
            var consumed = 0
            while (consumed < length) {
                val remaining = length - consumed
                if (remaining < 4) {
                    throw IOException("Malformed APK ZIP $headerKind extra field header")
                }
                val fieldHeader = ByteArray(4)
                input.readFully(fieldHeader)
                val fieldId = readU16(fieldHeader, 0)
                val dataLength = readU16(fieldHeader, 2)
                if (dataLength > remaining - 4) {
                    throw IOException("Malformed APK ZIP $headerKind extra field length")
                }
                if (fieldId == 0x0001) {
                    throw IOException("ZIP64 APK ZIP extra fields are unsupported")
                }
                input.seek(input.filePointer + dataLength)
                consumed += 4 + dataLength
            }
        } finally {
            input.seek(originalPosition)
        }
    }

    /**
     * Cross-check a central-directory record against its local header without
     * extracting or inflating the entry. The central directory remains the
     * source of the compressed byte count; local metadata must agree with it.
     */
    private fun validateLocalRecord(
        input: RandomAccessFile,
        localOffset: Long,
        centralOffset: Long,
        centralName: ByteArray,
        centralFlags: Int,
        centralMethod: Int,
        centralCrc: Long,
        centralCompressedSize: Long,
        centralUncompressedSize: Long,
    ): Long {
        val centralPosition = input.filePointer
        try {
            if (localOffset < 0L || centralOffset - localOffset < 30L) {
                throw IOException("APK ZIP local header is outside the data region")
            }
            input.seek(localOffset)
            val header = ByteArray(30)
            input.readFully(header)
            if (readU32(header, 0) != 0x04034b50L) {
                throw IOException("Invalid APK ZIP local-header signature")
            }
            val localFlags = readU16(header, 6)
            val localMethod = readU16(header, 8)
            val localCrc = readU32(header, 14)
            val localCompressedSize = readU32(header, 18)
            val localUncompressedSize = readU32(header, 22)
            val localNameLength = readU16(header, 26)
            val localExtraLength = readU16(header, 28)
            if (localNameLength !in 1..MAX_ENTRY_NAME_LENGTH ||
                localNameLength != centralName.size ||
                localFlags != centralFlags ||
                localMethod != centralMethod
            ) throw IOException("APK ZIP local and central headers disagree")
            validateGeneralPurposeFlags(localFlags, localMethod, "local")

            val dataStart = localOffset + 30L + localNameLength + localExtraLength
            if (dataStart > centralOffset) {
                throw IOException("APK ZIP local header fields cross the central-directory boundary")
            }
            val localName = ByteArray(localNameLength)
            input.readFully(localName)
            if (!localName.contentEquals(centralName)) {
                throw IOException("APK ZIP local and central filenames disagree")
            }
            validateExtraFields(input, input.filePointer, localExtraLength, "local")

            val hasDescriptor = centralFlags and 0x0008 != 0
            if (!hasDescriptor) {
                if (localCrc != centralCrc ||
                    localCompressedSize != centralCompressedSize ||
                    localUncompressedSize != centralUncompressedSize
                ) throw IOException("APK ZIP local sizes or CRC disagree with central metadata")
            } else if (
                (localCrc != 0L && localCrc != centralCrc) ||
                (localCompressedSize != 0L && localCompressedSize != centralCompressedSize) ||
                (localUncompressedSize != 0L && localUncompressedSize != centralUncompressedSize)
            ) {
                throw IOException("APK ZIP local placeholders disagree with central metadata")
            }

            if (centralCompressedSize > centralOffset - dataStart) {
                throw IOException("APK ZIP entry data crosses the central-directory boundary")
            }
            val dataEnd = dataStart + centralCompressedSize
            return if (hasDescriptor) {
                validateDataDescriptor(
                    input, dataEnd, centralOffset,
                    centralCrc, centralCompressedSize, centralUncompressedSize,
                )
            } else {
                dataEnd
            }
        } finally {
            input.seek(centralPosition)
        }
    }

    /** Validate the ZIP32 data descriptor, with or without its optional signature. */
    private fun validateDataDescriptor(
        input: RandomAccessFile,
        dataEnd: Long,
        centralOffset: Long,
        expectedCrc: Long,
        expectedCompressedSize: Long,
        expectedUncompressedSize: Long,
    ): Long {
        fun matches(withSignature: Boolean): Boolean {
            val length = if (withSignature) 16L else 12L
            if (dataEnd < 0L || centralOffset - dataEnd < length) return false
            input.seek(dataEnd)
            val bytes = ByteArray(length.toInt())
            input.readFully(bytes)
            val valuesOffset = if (withSignature) {
                if (readU32(bytes, 0) != 0x08074b50L) return false
                4
            } else {
                0
            }
            return readU32(bytes, valuesOffset) == expectedCrc &&
                readU32(bytes, valuesOffset + 4) == expectedCompressedSize &&
                readU32(bytes, valuesOffset + 8) == expectedUncompressedSize
        }

        if (matches(withSignature = true)) return dataEnd + 16L
        if (matches(withSignature = false)) return dataEnd + 12L
        throw IOException("APK ZIP data descriptor disagrees with central metadata")
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset > bytes.size - 2) throw IOException("Truncated APK ZIP end record")
        return (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        readU16(bytes, offset).toLong() or (readU16(bytes, offset + 2).toLong() shl 16)
}
