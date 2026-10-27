package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.zip.CRC32
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


    private fun zipWithEntryComment(name: String, entryName: String, comment: String): File {
        val file = temp.newFile(name)
        ZipOutputStream(file.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("AndroidManifest.xml"))
            output.write(byteArrayOf(1, 2, 3))
            output.closeEntry()
            output.putNextEntry(ZipEntry(entryName).apply { this.comment = comment })
            output.write(byteArrayOf(4, 5, 6))
            output.closeEntry()
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

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { value, index ->
            value or ((bytes[offset + index].toLong() and 255L) shl (index * 8))
        }

    private fun writeU64(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 8) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }

    private fun littleEndian64(value: Long): ByteArray =
        ByteArray(8) { index -> (value ushr (index * 8)).toByte() }

    private data class SigningBlockFixture(
        val bytes: ByteArray,
        val blockStart: Int,
        val centralOffset: Int,
        val sizeField: Long,
    )

    private fun withSigningBlock(original: ByteArray, pairCount: Int = 1): SigningBlockFixture {
        val oldEnd = endRecord(original)
        val oldCentralOffset = readU32(original, oldEnd + 16).toInt()
        val pairBytes = java.io.ByteArrayOutputStream().apply {
            repeat(pairCount) { index ->
                write(littleEndian64(4L))
                val id = 0x12345678L + index
                write(byteArrayOf(
                    id.toByte(),
                    (id ushr 8).toByte(),
                    (id ushr 16).toByte(),
                    (id ushr 24).toByte(),
                )) // opaque ID; the preflight validates framing only
            }
        }.toByteArray()
        val sizeField = pairBytes.size.toLong() + 24L
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        val block = java.io.ByteArrayOutputStream().apply {
            write(littleEndian64(sizeField))
            write(pairBytes)
            write(littleEndian64(sizeField))
            write(magic)
        }.toByteArray()
        val output = ByteArray(original.size + block.size)
        System.arraycopy(original, 0, output, 0, oldCentralOffset)
        System.arraycopy(block, 0, output, oldCentralOffset, block.size)
        System.arraycopy(
            original, oldCentralOffset, output, oldCentralOffset + block.size,
            original.size - oldCentralOffset,
        )
        val newEnd = oldEnd + block.size
        val newCentralOffset = oldCentralOffset + block.size
        writeU32(output, newEnd + 16, newCentralOffset.toLong())
        return SigningBlockFixture(output, oldCentralOffset, newCentralOffset, sizeField)
    }

    /** Mark a central-directory entry as Unix-created with the supplied POSIX mode. */
    private fun withUnixMode(file: File, entryName: String, mode: Int): File {
        val bytes = file.readBytes()
        val central = centralRecordOffset(bytes, entryName)
        writeU16(bytes, central + 4, (3 shl 8) or 20) // Unix host, ZIP version 2.0
        writeU32(bytes, central + 38, mode.toLong() shl 16)
        file.writeBytes(bytes)
        return file
    }

    private fun storedZip(name: String, vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile(name)
        ZipOutputStream(file.outputStream()).use { output ->
            for ((path, bytes) in entries) {
                val crc = CRC32().apply { update(bytes) }.value
                output.putNextEntry(ZipEntry(path).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc
                })
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
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

    @Test fun acceptsConsistentUnixRegularFileAndDirectoryModes() {
        val apk = zip("unix-types-valid.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
            "assets/" to byteArrayOf(),
        )
        withUnixMode(apk, "AndroidManifest.xml", 0x81a4) // regular file, 0644
        withUnixMode(apk, "classes.dex", 0x81a4)
        withUnixMode(apk, "assets/", 0x41ed) // directory, 0755

        assertEquals(3, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsUnixSymbolicLinkEntryType() {
        val apk = zip("unix-symlink.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        withUnixMode(apk, "classes.dex", 0xa1ff)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("symbolic-link"))
    }

    @Test fun rejectsUnixSpecialFilesystemEntryType() {
        val apk = zip("unix-fifo.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        withUnixMode(apk, "classes.dex", 0x11a4) // FIFO

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("special filesystem"))
    }

    @Test fun rejectsUnixFileTypeThatDisagreesWithDirectoryPath() {
        val apk = zip("unix-directory-type-mismatch.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "assets/" to byteArrayOf(),
        )
        withUnixMode(apk, "assets/", 0x81a4) // regular-file type with trailing slash

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("regular-file type"))
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
            val error = assertThrows(path, IOException::class.java) { ApkArchivePreflight.check(apk) }
            assertTrue(path, error.message.orEmpty().contains("central"))
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
            val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
            assertTrue(error.message.orEmpty().contains("central directory"))
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


    @Test fun rejectsDuplicateCentralNamesBeforeZipFileConstruction() {
        val apk = zip("duplicate-central-names.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "assets/a" to byteArrayOf(4),
            "assets/b" to byteArrayOf(5),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "assets/b")
        val local = readU32Value(bytes, central + 42).toInt()
        val centralNameLength = readU16Value(bytes, central + 28)
        val localNameLength = readU16Value(bytes, local + 26)
        assertEquals(8, centralNameLength)
        assertEquals(8, localNameLength)
        // Keep local/central metadata consistent while aliasing the second
        // record to the first logical path. The preflight must reject it
        // before java.util.zip.ZipFile constructs its entry table.
        bytes[central + 46 + centralNameLength - 1] = 'a'.code.toByte()
        bytes[local + 30 + localNameLength - 1] = 'a'.code.toByte()
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("central directory has duplicate"))
    }

    @Test fun rejectsCanonicallyEquivalentUnicodeZipPaths() {
        val apk = zip("unicode-alias.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/caf\u00E9.png" to byteArrayOf(2),
            "assets/cafe\u0301.png" to byteArrayOf(3),
        )
        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("central directory"))
    }

    @Test fun rejectsUnicodeAliasOfImplicitDirectory() {
        val apk = zip("unicode-parent-alias.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/caf\u00E9/icon.png" to byteArrayOf(2),
            "assets/cafe\u0301" to byteArrayOf(3),
        )
        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("central directory"))
    }

    @Test fun acceptsVisibleNonLatinZipPaths() {
        val apk = zip("unicode-valid.apk",
            "AndroidManifest.xml" to byteArrayOf(1),
            "assets/صور/أيقونة.png" to byteArrayOf(2, 3),
        )
        assertEquals(2, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsMalformedUtf8EntryNamesWithOrWithoutEfsFlag() {
        for (efsEnabled in listOf(false, true)) {
            val apk = zip(
                "malformed-utf8-name-$efsEnabled.apk",
                "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
                "assets/ab.txt" to byteArrayOf(4, 5, 6),
            )
            val bytes = apk.readBytes()
            val central = centralRecordOffset(bytes, "assets/ab.txt")
            val local = readU32Value(bytes, central + 42).toInt()
            val originalFlags = readU16Value(bytes, central + 8)
            val flags = if (efsEnabled) originalFlags or 0x0800 else originalFlags and 0x0800.inv()
            writeU16(bytes, central + 8, flags)
            writeU16(bytes, local + 6, flags)
            // Replace the two ASCII bytes "ab" with an invalid UTF-8 lead byte
            // followed by a non-continuation byte, preserving the ZIP name length.
            bytes[central + 46 + 7] = 0xC3.toByte()
            bytes[central + 46 + 8] = 0x28
            bytes[local + 30 + 7] = 0xC3.toByte()
            bytes[local + 30 + 8] = 0x28
            apk.writeBytes(bytes)

            val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
            assertTrue(error.message.orEmpty().contains("entry name is not valid UTF-8"))
        }
    }

    @Test fun acceptsValidUtf8EntryNameWithoutEfsFlag() {
        val apk = zip(
            "utf8-name-without-efs.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "assets/صور/أيقونة.png" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "assets/صور/أيقونة.png")
        val local = readU32Value(bytes, central + 42).toInt()
        writeU16(bytes, central + 8, readU16Value(bytes, central + 8) and 0x0800.inv())
        writeU16(bytes, local + 6, readU16Value(bytes, local + 6) and 0x0800.inv())
        apk.writeBytes(bytes)

        assertEquals(2, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsMalformedUtf8EntryCommentBeforeZipFileParsing() {
        val apk = zipWithEntryComment("malformed-utf8-comment.apk", "assets/comment.txt", "ab")
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "assets/comment.txt")
        val local = readU32Value(bytes, central + 42).toInt()
        val flags = readU16Value(bytes, central + 8) or 0x0800
        writeU16(bytes, central + 8, flags)
        writeU16(bytes, local + 6, flags)
        val nameLength = readU16Value(bytes, central + 28)
        val extraLength = readU16Value(bytes, central + 30)
        val commentLength = readU16Value(bytes, central + 32)
        assertEquals(2, commentLength)
        val commentOffset = central + 46 + nameLength + extraLength
        bytes[commentOffset] = 0xC3.toByte()
        bytes[commentOffset + 1] = 0x28
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("entry comment is not valid UTF-8"))
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
        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("directory entry advertises uncompressed data"))
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

    @Test fun rejectsDuplicateNamesWhenCentralRecordsAliasTheSameLocalRecord() {
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
        // The new earlier guard catches the duplicate logical path before the
        // overlapping local-range check can be reached.
        assertTrue(error.message.orEmpty().contains("central directory has duplicate"))
    }


    @Test fun rejectsUnsupportedCompressionMethodBeforeZipFileParsing() {
        val apk = zip("unsupported-method.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(32) { it.toByte() },
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        writeU16(bytes, central + 10, 99)
        writeU16(bytes, local + 8, 99)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("Unsupported APK ZIP compression method"))
    }

    @Test fun rejectsStoredEntryWithDifferentCompressedAndExpandedSizesBeforeZipFileParsing() {
        val apk = zip("stored-size-mismatch.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(128),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        // Keep both headers in agreement while creating an impossible STORED
        // record: STORED data cannot have different compressed/expanded sizes.
        writeU16(bytes, central + 10, ZipEntry.STORED)
        writeU16(bytes, local + 8, ZipEntry.STORED)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("STORED ZIP entry sizes disagree"))
    }

    @Test fun rejectsAggregateDeclaredExpansionBeforeZipFileParsing() {
        val apk = zip("aggregate-expansion.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4),
            "assets/payload.bin" to byteArrayOf(5),
        )
        val bytes = apk.readBytes()
        listOf("classes.dex" to 3L, "assets/payload.bin" to 2L).forEach { (name, gib) ->
            val central = centralRecordOffset(bytes, name)
            val local = readU32Value(bytes, central + 42).toInt()
            val expandedSize = gib * 1024L * 1024L * 1024L
            // Remove the data-descriptor flag and make the local metadata agree
            // so the regression reaches the aggregate guard before any later
            // descriptor/payload mismatch can reject the archive for another reason.
            val flags = readU16Value(bytes, central + 8) and 0x0008.inv()
            val crc = readU32Value(bytes, central + 16)
            val compressedSize = readU32Value(bytes, central + 20)
            writeU16(bytes, central + 8, flags)
            writeU16(bytes, local + 6, flags)
            writeU32(bytes, local + 14, crc)
            writeU32(bytes, local + 18, compressedSize)
            writeU32(bytes, central + 24, expandedSize)
            writeU32(bytes, local + 22, expandedSize)
        }
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("aggregate expansion exceeds inspection limits"))
    }

    @Test fun rejectsNonemptyEntryWithZeroCompressedSizeBeforeZipFileParsing() {
        val apk = zip("zero-compressed-size.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(32) { it.toByte() },
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        writeU32(bytes, central + 20, 0)
        writeU32(bytes, local + 18, 0)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("impossible compressed size metadata"))
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


    @Test fun rejectsReservedAndUnsupportedGeneralPurposeFlagsBeforeZipFileParsing() {
        // Bits 4/5 are reserved or patched-data modes; bits 7-10, 12, 14 and
        // 15 are unused/reserved in the ZIP32 subset accepted by this inspector.
        val unsupportedFlags = listOf(
            0x0010, 0x0020, 0x0080, 0x0100, 0x0200,
            0x0400, 0x1000, 0x4000, 0x8000,
        )
        unsupportedFlags.forEachIndexed { index, flag ->
            val apk = zip("unsupported-flags-$index.apk",
                "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
                "classes.dex" to byteArrayOf(4, 5, 6),
            )
            val bytes = apk.readBytes()
            val central = centralRecordOffset(bytes, "classes.dex")
            val local = readU32Value(bytes, central + 42).toInt()
            writeU16(bytes, central + 8, readU16Value(bytes, central + 8) or flag)
            writeU16(bytes, local + 6, readU16Value(bytes, local + 6) or flag)
            apk.writeBytes(bytes)

            val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
            assertTrue(
                "flag 0x\${flag.toString(16)} should be rejected as unsupported",
                error.message.orEmpty().contains("Unsupported APK ZIP central general-purpose flags"),
            )
        }
    }

    @Test fun rejectsDeflateCompressionOptionsOnStoredEntries() {
        val apk = zip("stored-deflate-option.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to ByteArray(32) { it.toByte() },
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        // Keep both headers consistent while declaring DEFLATE-only option bits
        // on a STORED entry. The flags check must fail before size parsing.
        writeU16(bytes, central + 10, ZipEntry.STORED)
        writeU16(bytes, local + 8, ZipEntry.STORED)
        val flags = readU16Value(bytes, central + 8) or 0x0002
        writeU16(bytes, central + 8, flags)
        writeU16(bytes, local + 6, flags)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("compression-option flags require DEFLATED method"))
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


    @Test fun acceptsWellFormedUnknownExtraFieldsWithoutInterpretingTheirPayloads() {
        val apk = temp.newFile("unknown-extra-field.apk")
        ZipOutputStream(apk.outputStream()).use { output ->
            val manifest = ZipEntry("AndroidManifest.xml")
            // Vendor/private extra-field ID 0x1234, two opaque data bytes.
            manifest.extra = byteArrayOf(0x34, 0x12, 0x02, 0x00, 0x01, 0x02)
            output.putNextEntry(manifest)
            output.write(byteArrayOf(1, 2, 3))
            output.closeEntry()
        }

        assertEquals(1, ApkArchivePreflight.check(apk).entries)
    }

    @Test fun rejectsTruncatedLocalExtraFieldHeader() {
        val apk = zip("truncated-local-extra.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "classes.dex")
        val local = readU32Value(bytes, central + 42).toInt()
        // Claim one local extra-field byte although a TLV header needs four.
        writeU16(bytes, local + 28, 1)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("extra field header"))
    }

    @Test fun rejectsTruncatedCentralExtraFieldHeader() {
        val apk = zip("truncated-central-extra.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "AndroidManifest.xml")
        // The next central record follows immediately, so one claimed byte
        // cannot form a complete extra-field TLV header.
        writeU16(bytes, central + 30, 1)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("extra field header"))
    }

    @Test fun rejectsZip64ExtraFieldEvenWhenZip32SizeFieldsAreNotSentinels() {
        val apk = temp.newFile("zip64-extra-field.apk")
        ZipOutputStream(apk.outputStream()).use { output ->
            val manifest = ZipEntry("AndroidManifest.xml")
            manifest.extra = byteArrayOf(0x34, 0x12, 0x00, 0x00)
            output.putNextEntry(manifest)
            output.write(byteArrayOf(1, 2, 3))
            output.closeEntry()
        }
        val bytes = apk.readBytes()
        val central = centralRecordOffset(bytes, "AndroidManifest.xml")
        val nameLength = readU16Value(bytes, central + 28)
        val extraOffset = central + 46 + nameLength
        // Replace only the extra-field ID with ZIP64 (0x0001), leaving the
        // ordinary ZIP32 size fields and a well-framed zero-length payload.
        writeU16(bytes, extraOffset, 0x0001)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("ZIP64"))
    }


    @Test fun acceptsWellFormedSigningBlockWithUnknownId() {
        val original = zip(
            "signed-block-source.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        ).readBytes()
        val fixture = withSigningBlock(original)
        val apk = temp.newFile("signed-block-valid.apk").apply { writeBytes(fixture.bytes) }
        val result = ApkArchivePreflight.check(apk)
        assertEquals(2, result.entries)
    }

    @Test fun rejectsSigningBlockHeaderFooterMismatchAndInvalidPairLength() {
        val original = zip(
            "signed-block-source-2.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        ).readBytes()
        val fixture = withSigningBlock(original)

        val badHeader = fixture.bytes.copyOf()
        writeU64(badHeader, fixture.blockStart, fixture.sizeField + 1)
        val headerError = assertThrows(IOException::class.java) {
            ApkArchivePreflight.check(temp.newFile("signed-block-bad-header.apk").apply { writeBytes(badHeader) })
        }
        assertTrue(headerError.message.orEmpty().contains("APK signing block"))

        val badFooter = fixture.bytes.copyOf()
        writeU64(badFooter, fixture.centralOffset - 24, fixture.sizeField + 1)
        val footerError = assertThrows(IOException::class.java) {
            ApkArchivePreflight.check(temp.newFile("signed-block-bad-footer.apk").apply { writeBytes(badFooter) })
        }
        assertTrue(footerError.message.orEmpty().contains("APK signing block"))

        val badPair = fixture.bytes.copyOf()
        writeU64(badPair, fixture.blockStart + 8, Long.MAX_VALUE)
        val pairError = assertThrows(IOException::class.java) {
            ApkArchivePreflight.check(temp.newFile("signed-block-bad-pair.apk").apply { writeBytes(badPair) })
        }
        assertTrue(pairError.message.orEmpty().contains("APK signing block"))
    }

    @Test fun rejectsSigningBlockThatOverlapsReferencedLocalEntryData() {
        val classes = ByteArray(256) { (it * 13).toByte() }
        val apk = storedZip(
            "signed-block-overlap.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to classes,
        )
        val bytes = apk.readBytes()
        val end = endRecord(bytes)
        val centralOffset = readU32(bytes, end + 16).toInt()
        val sizeField = 48L // 24-byte pair region plus 24-byte footer
        val totalSize = sizeField + 8L
        val blockStart = centralOffset - totalSize.toInt()
        assertTrue("Fixture must overlap the stored classes.dex payload", blockStart > 0)
        writeU64(bytes, blockStart, sizeField)
        writeU64(bytes, blockStart + 8, 16L)
        writeU32(bytes, blockStart + 16, 0x12345678L)
        java.util.Arrays.fill(bytes, blockStart + 20, centralOffset - 24, 0x55.toByte())
        writeU64(bytes, centralOffset - 24, sizeField)
        val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
        System.arraycopy(magic, 0, bytes, centralOffset - 16, magic.size)

        val error = assertThrows(IOException::class.java) {
            ApkArchivePreflight.check(temp.newFile("signed-block-overlap-forged.apk").apply { writeBytes(bytes) })
        }
        assertTrue(error.message.orEmpty().contains("overlaps local ZIP entry data"))
    }

    @Test fun rejectsSigningBlockAboveTheConfiguredSizeLimit() {
        val original = zip(
            "signed-block-source-oversize.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        ).readBytes()
        val fixture = withSigningBlock(original)
        val oversized = fixture.bytes.copyOf()
        writeU64(oversized, fixture.centralOffset - 24, 64L * 1024 * 1024 - 7L)
        val error = assertThrows(IOException::class.java) {
            ApkArchivePreflight.check(temp.newFile("signed-block-oversized.apk").apply { writeBytes(oversized) })
        }
        assertTrue(error.message.orEmpty().contains("size is outside inspection limits"))
    }

    @Test fun rejectsSigningBlockWithTooManyPairs() {
        val original = zip(
            "signed-block-source-many-pairs.apk",
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        ).readBytes()
        val fixture = withSigningBlock(original, pairCount = 1_025)
        val error = assertThrows(IOException::class.java) {
            ApkArchivePreflight.check(temp.newFile("signed-block-many-pairs.apk").apply { writeBytes(fixture.bytes) })
        }
        assertTrue(error.message.orEmpty().contains("too many ID/value pairs"))
    }

    @Test fun rejectsAmbiguousEndRecordSignaturesInsideArchiveComment() {
        val apk = zipWithComment(
            "ambiguous-eocd.apk",
            "A".repeat(40),
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val realEnd = endRecord(bytes)
        val commentStart = realEnd + 22
        val commentLength = readU16Value(bytes, realEnd + 20)
        assertTrue("fixture comment must fit a complete false EOCD", commentLength >= 22)

        // Embed a second signature in the comment and make its own comment-length
        // field end exactly at EOF. Both candidates are byte-plausible, so reject
        // the archive before ZipFile can choose a different interpretation.
        val fake = commentStart
        System.arraycopy(byteArrayOf(0x50, 0x4b, 0x05, 0x06), 0, bytes, fake, 4)
        writeU16(bytes, fake + 4, 0)
        writeU16(bytes, fake + 6, 0)
        writeU16(bytes, fake + 8, 0)
        writeU16(bytes, fake + 10, 0)
        writeU32(bytes, fake + 12, 0)
        writeU32(bytes, fake + 16, 0)
        writeU16(bytes, fake + 20, commentLength - 22)
        apk.writeBytes(bytes)

        val error = assertThrows(IOException::class.java) { ApkArchivePreflight.check(apk) }
        assertTrue(error.message.orEmpty().contains("end record is ambiguous"))
    }

    @Test fun ignoresEocdSignatureInCommentWhenItsLengthDoesNotReachEof() {
        val apk = zipWithComment(
            "nonmatching-eocd-comment.apk",
            "A".repeat(40),
            "AndroidManifest.xml" to byteArrayOf(1, 2, 3),
            "classes.dex" to byteArrayOf(4, 5, 6),
        )
        val bytes = apk.readBytes()
        val realEnd = endRecord(bytes)
        val commentStart = realEnd + 22
        val commentLength = readU16Value(bytes, realEnd + 20)
        val fake = commentStart
        System.arraycopy(byteArrayOf(0x50, 0x4b, 0x05, 0x06), 0, bytes, fake, 4)
        writeU16(bytes, fake + 4, 0)
        writeU16(bytes, fake + 6, 0)
        writeU16(bytes, fake + 8, 0)
        writeU16(bytes, fake + 10, 0)
        writeU32(bytes, fake + 12, 0)
        writeU32(bytes, fake + 16, 0)
        writeU16(bytes, fake + 20, commentLength - 21)
        apk.writeBytes(bytes)

        assertEquals(2, ApkArchivePreflight.check(apk).entries)
    }

}
