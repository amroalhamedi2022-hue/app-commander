package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkArchivePreflightTest {
    @get:Rule val temp = TemporaryFolder()

    private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile(name)
        ZipOutputStream(file.outputStream()).use { output ->
            for ((path, bytes) in entries) {
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    private fun zipWithComment(name: String, comment: String, vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile(name)
        ZipOutputStream(file.outputStream()).use { output ->
            output.setComment(comment)
            for ((path, bytes) in entries) {
                output.putNextEntry(ZipEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }

    private fun endRecord(bytes: ByteArray): Int {
        for (offset in bytes.size - 22 downTo maxOf(0, bytes.size - 22 - 0xffff)) {
            if (offset + 22 > bytes.size) continue
            val signature = bytes[offset].toInt() and 255
            if (signature != 0x50 || (bytes[offset + 1].toInt() and 255) != 0x4b ||
                (bytes[offset + 2].toInt() and 255) != 0x05 ||
                (bytes[offset + 3].toInt() and 255) != 0x06
            ) continue
            val commentLength = (bytes[offset + 20].toInt() and 255) or
                ((bytes[offset + 21].toInt() and 255) shl 8)
            if (offset + 22 + commentLength == bytes.size) return offset
        }
        error("ZIP end record not found")
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 4) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }

    @Test fun acceptsBoundedArchiveWithManifestWithoutClaimingSignatureValidity() {
        val apk = zip("structural.apk",
            "AndroidManifest.xml" to byteArrayOf(3, 0, 8, 0),
            "classes.dex" to ByteArray(19) { it.toByte() },
            "assets/data.bin" to ByteArray(7),
            "assets/empty/" to byteArrayOf(),
        )
        val result = ApkArchivePreflight.check(apk)
        assertEquals(4, result.entries)
        assertEquals(30L, result.declaredUncompressedBytes)
    }

    @Test fun rejectsMissingOrEmptyManifest() {
        val absent = zip("absent.apk", "classes.dex" to byteArrayOf(1))
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(absent) }
        val empty = zip("empty-manifest.apk", "AndroidManifest.xml" to byteArrayOf())
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(empty) }
    }

    @Test fun rejectsUnsafeArchivePathsWithoutExtractingAnything() {
        val unsafePaths = listOf(
            "../escape", "/absolute", "assets/../bad", "assets/./bad",
            "assets//bad", "assets//", "a\\b", "bad\nname",
            "a".repeat(ApkArchivePreflight.MAX_ENTRY_NAME_LENGTH + 1),
        )
        for ((index, path) in unsafePaths.withIndex()) {
            val apk = zip("unsafe-$index.apk",
                "AndroidManifest.xml" to byteArrayOf(1),
                path to byteArrayOf(2),
            )
            assertThrows(path, IOException::class.java) { ApkArchivePreflight.check(apk) }
        }
    }

    @Test fun rejectsFileDirectoryCollisionsInEitherEntryOrder() {
        val manifest = "AndroidManifest.xml" to byteArrayOf(1, 2, 3)
        val collisions = listOf(
            arrayOf("assets/icon" to byteArrayOf(4), "assets/icon/" to byteArrayOf()),
            arrayOf("assets/icon/" to byteArrayOf(), "assets/icon" to byteArrayOf(4)),
            arrayOf("assets/icon" to byteArrayOf(4), "assets/icon/child" to byteArrayOf(5)),
            arrayOf("assets/icon/child" to byteArrayOf(5), "assets/icon" to byteArrayOf(4)),
        )
        collisions.forEachIndexed { index, entries ->
            val apk = zip("collision-${index}.apk", manifest, *entries)
            assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        }
    }

    @Test fun acceptsOrdinaryNestedDirectoriesAndFiles() {
        val apk = zip("nested.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "assets/" to byteArrayOf(),
            "assets/icons/" to byteArrayOf(),
            "assets/icons/icon.png" to byteArrayOf(4, 5),
        )
        assertEquals(4, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsOversizedDeclaredManifestWithoutInflatingIt() {
        // A highly compressible archive still advertises its expanded size
        // in the central directory; the preflight must not extract the bytes.
        val apk = zip("oversized-manifest.apk",
            "AndroidManifest.xml" to ByteArray((ApkArchivePreflight.MAX_MANIFEST_BYTES + 1).toInt()),
        )
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsManifestCrcMismatchWithoutInflatingOtherEntries() {
        val apk = zip("bad-manifest-crc.apk",
            "AndroidManifest.xml" to byteArrayOf(3, 0, 8, 0, 12, 34, 56),
            "classes.dex" to ByteArray(128),
        )
        val bytes = apk.readBytes()
        // Central directory header 0x02014b50, CRC32 at offset 16.
        // Alter ONLY the advertised CRC, leaving compressed payload intact.
        val signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        val index = (0..bytes.size - 20).firstOrNull { offset ->
            signature.indices.all { bytes[offset + it] == signature[it] }
        } ?: error("ZIP central directory not found")
        bytes[index + 16] = (bytes[index + 16].toInt() xor 1).toByte()
        apk.writeBytes(bytes)
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsInvalidOrEmptyZipBeforeAndroidParser() {
        val invalid = temp.newFile("invalid.apk").apply { writeText("not a ZIP archive") }
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(invalid) }
        val empty = temp.newFile("empty.apk")
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(empty) }
    }

    @Test fun acceptsOrdinaryApkZipCommentAfterBoundedEndRecordScan() {
        val apk = zipWithComment(
            "comment.apk", "normal APK comment",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5),
        )
        assertEquals(2, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsExcessiveDeclaredEntryCountBeforeOpeningZipFile() {
        val apk = zip(
            "too-many-entries.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5),
        )
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        writeU16(bytes, end + 8, ApkArchivePreflight.MAX_ENTRIES + 1)
        writeU16(bytes, end + 10, ApkArchivePreflight.MAX_ENTRIES + 1)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("too many ZIP entries"))
    }

    @Test fun rejectsOversizedCentralDirectoryBeforeOpeningZipFile() {
        val apk = zip(
            "oversized-central-directory.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
        )
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        writeU32(bytes, end + 12, ApkArchivePreflight.MAX_CENTRAL_DIRECTORY_BYTES + 1)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("central directory exceeds inspection limits"))
    }

    @Test fun rejectsCentralDirectoryCountThatDoesNotMatchActualRecords() {
        val apk = zip(
            "central-count-mismatch.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5),
        )
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        val entries = (bytes[end + 10].toInt() and 255) or
            ((bytes[end + 11].toInt() and 255) shl 8)
        writeU16(bytes, end + 8, entries + 1)
        writeU16(bytes, end + 10, entries + 1)
        apk.writeBytes(bytes)
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsZip64SentinelInCentralEntryBeforeOpeningZipFile() {
        val apk = zip("zip64-entry.apk", "AndroidManifest.xml" to byteArrayOf(1, 2, 3))
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        val centralOffset = (0..3).fold(0L) { value, index ->
            value or ((bytes[end + 16 + index].toLong() and 255L) shl (index * 8))
        }.toInt()
        // ZIP32 file size is small; an entry-level ZIP64 sentinel must not
        // bypass the archive-level ZIP64 check and reach ZipFile allocation.
        writeU32(bytes, centralOffset + 24, 0xffffffffL)
        apk.writeBytes(bytes)
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsTruncatedZipEndRecordBeforeOpeningZipFile() {
        val apk = zip(
            "truncated-end-record.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
        )
        val bytes = apk.readBytes()
        apk.writeBytes(bytes.copyOf(bytes.size - 1))

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("end record is missing or malformed"))
    }

    @Test fun rejectsInvisibleUnicodeSpoofingControlsInEntryPaths() {
        val unsafe = listOf(
            "assets/icon\u202Egnp", // bidirectional override
            "assets/hidden\u200Bname", // zero-width space
            "assets/soft\u00ADhyphen",
            "assets/isolate\u2066name",
            "assets/arabic\u061Cmark",
            "assets/feff\uFEFFname",
            "assets/c1\u0085name",
        )
        unsafe.forEachIndexed { index, path ->
            val apk = zip("unicode-spoof-$index.apk",
                "AndroidManifest.xml" to byteArrayOf(1),
                path to byteArrayOf(2),
            )
            assertThrows(path, IOException::class.java) { ApkArchivePreflight.check(apk) }
        }
    }

    @Test fun rejectsCanonicallyEquivalentUnicodeZipPaths() {
        val apk = zip("unicode-alias.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/caf\u00E9.png" to byteArrayOf(2),
            "assets/cafe\u0301.png" to byteArrayOf(3),
        )
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsUnicodeAliasOfImplicitDirectory() {
        val apk = zip("unicode-parent-alias.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/caf\u00E9/icon.png" to byteArrayOf(2),
            "assets/cafe\u0301" to byteArrayOf(3),
        )
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun acceptsVisibleNonLatinZipPaths() {
        val apk = zip("unicode-valid.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/صور/أيقونة.png" to byteArrayOf(2, 3),
        )
        assertEquals(2, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsImpossibleCompressedSizeInCentralDirectory() {
        val apk = zip("bad-compressed-size.apk",
            "AndroidManifest.xml" to byteArrayOf(3, 0, 8, 0),
            "classes.dex" to ByteArray(128) { it.toByte() },
        )
        val bytes = apk.readBytes()
        val signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        val index = (0..bytes.size - 24).firstOrNull { offset ->
            signature.indices.all { bytes[offset + it] == signature[it] }
        } ?: error("ZIP central directory not found")
        // Compressed-size field at offset 20 of first central-directory entry.
        // Its uncompressed size remains nonzero.
        for (i in 20..23) bytes[index + i] = 0
        apk.writeBytes(bytes)
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsDirectoryEntriesWithPayloads() {
        val apk = zip("directory-payload.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/" to byteArrayOf(7, 8, 9),
        )
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }


    private fun readU16Value(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8)

    private fun readU32Value(bytes: ByteArray, offset: Int): Long =
        readU16Value(bytes, offset).toLong() or
            (readU16Value(bytes, offset + 2).toLong() shl 16)

    private fun centralRecordOffset(bytes: ByteArray, name: String): Int {
        val end = endRecord(bytes)
        val count = readU16Value(bytes, end + 10)
        var cursor = readU32Value(bytes, end + 16).toInt()
        repeat(count) {
            assertTrue("central record header must fit", cursor >= 0 && cursor + 46 <= bytes.size)
            assertEquals(0x02014b50L, readU32Value(bytes, cursor))
            val nameLength = readU16Value(bytes, cursor + 28)
            val extraLength = readU16Value(bytes, cursor + 30)
            val commentLength = readU16Value(bytes, cursor + 32)
            val actualName = String(bytes, cursor + 46, nameLength, Charsets.UTF_8)
            if (actualName == name) return cursor
            cursor += 46 + nameLength + extraLength + commentLength
        }
        error("Central-directory entry not found: $name")
    }

    @Test fun rejectsInvalidLocalHeaderSignatureForNonManifestEntry() {
        val apk = zip("bad-local-signature.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        bytes[local] = 0
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsLocalFilenameThatDiffersFromCentralDirectory() {
        val apk = zip("local-name-mismatch.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        bytes[local + 30] = 'X'.code.toByte()
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsLocalCompressionMethodThatDiffersFromCentralDirectory() {
        val apk = zip("local-method-mismatch.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        val centralMethod = readU16Value(bytes, central + 10)
        writeU16(bytes, local + 8, if (centralMethod == 0) 8 else 0)
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsLocalSizeMismatchWhenDescriptorFlagIsCleared() {
        val apk = zip("local-size-mismatch.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6, 7),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        val flags = readU16Value(bytes, central + 8) and 0x0008.inv()
        writeU16(bytes, central + 8, flags)
        writeU16(bytes, local + 6, flags)
        writeU32(bytes, local + 14, readU32Value(bytes, central + 16))
        writeU32(bytes, local + 18, 0)
        writeU32(bytes, local + 22, 0)
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsDataDescriptorThatDisagreesWithCentralCrc() {
        val apk = zip("bad-data-descriptor.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(64) { (it * 7).toByte() },
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        val localNameLength = readU16Value(bytes, local + 26)
        val localExtraLength = readU16Value(bytes, local + 28)
        val compressedSize = readU32Value(bytes, central + 20).toInt()
        val dataEnd = local + 30 + localNameLength + localExtraLength + compressedSize
        val hasSignature = readU32Value(bytes, dataEnd) == 0x08074b50L
        val crcOffset = dataEnd + if (hasSignature) 4 else 0
        bytes[crcOffset] = (bytes[crcOffset].toInt() xor 1).toByte()
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsTwoCentralRecordsAliasingTheSameLocalRecord() {
        val apk = zip("overlapping-local-records.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "a.txt" to byteArrayOf(4),
            "b.txt" to byteArrayOf(5),
        )
        val bytes = apk.readBytes()
        val first = centralRecordOffset(bytes, "a.txt")
        val second = centralRecordOffset(bytes, "b.txt")
        val firstLength = 46 + readU16Value(bytes, first + 28) +
            readU16Value(bytes, first + 30) + readU16Value(bytes, first + 32)
        val secondLength = 46 + readU16Value(bytes, second + 28) +
            readU16Value(bytes, second + 30) + readU16Value(bytes, second + 32)
        assertEquals(firstLength, secondLength)
        System.arraycopy(bytes, first, bytes, second, firstLength)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("local records overlap"))
    }


    @Test fun rejectsEncryptedEntryFlagsBeforeZipFileParsing() {
        val apk = zip("encrypted-entry.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        writeU16(bytes, central + 8, readU16Value(bytes, central + 8) or 0x0001)
        writeU16(bytes, local + 6, readU16Value(bytes, local + 6) or 0x0001)
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsEntryPayloadThatCrossesTheCentralDirectoryBoundary() {
        val apk = zip("entry-crosses-central-directory.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(32) { (it * 3).toByte() },
        )
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        val centralOffset = readU32Value(bytes, end + 16)
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        val dataStart = local.toLong() + 30L +
            readU16Value(bytes, local + 26) + readU16Value(bytes, local + 28)
        writeU32(bytes, central + 20, centralOffset - dataStart + 1L)
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

    @Test fun rejectsDataDescriptorTruncatedByCentralDirectoryBoundary() {
        val apk = zip("descriptor-crosses-central-directory.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(32) { (it * 5).toByte() },
        )
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        val centralOffset = readU32Value(bytes, end + 16)
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        val dataStart = local.toLong() + 30L +
            readU16Value(bytes, local + 26) + readU16Value(bytes, local + 28)
        val originalCompressedSize = readU32Value(bytes, central + 20)
        val dataEnd = dataStart + originalCompressedSize
        val descriptorLength = (centralOffset - dataEnd).toInt()
        assertTrue("last entry must have a complete ZIP32 descriptor", descriptorLength in 12..16)
        writeU32(bytes, central + 20, originalCompressedSize + descriptorLength - 8L)
        apk.writeBytes(bytes)

        assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
    }

}
