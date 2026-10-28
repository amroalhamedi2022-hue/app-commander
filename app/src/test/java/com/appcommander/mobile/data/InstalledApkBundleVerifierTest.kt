package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class InstalledApkBundleVerifierTest {
    private fun archive(vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                // Match the canonical exporter so each fixture reaches the
                // behavior under test instead of failing on unrelated ZIP extras.
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun exported(): ByteArray {
        val output = ByteArrayOutputStream()
        InstalledApkBundleExport.write(
            "com.example.sample",
            listOf(
                InstalledApkBundleExport.Source("base.apk", 4) {
                    ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
                },
                InstalledApkBundleExport.Source("split_001.apk", 3) {
                    ByteArrayInputStream(byteArrayOf(5, 6, 7))
                },
            ),
            output,
        )
        return output.toByteArray()
    }

    @Test fun exportedBundleRoundTripsThroughIndependentStreamingVerifier() {
        val result = InstalledApkBundleVerifier.verify(ByteArrayInputStream(exported()))
        assertEquals("com.example.sample", result.packageName)
        assertEquals(7L, result.totalApkBytes)
        assertEquals(listOf("base.apk", "split_001.apk"), result.entries.map { it.name })
    }

    @Test fun rejectsMissingManifestAndUnexpectedEntries() {
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive("base.apk" to byteArrayOf(1))))
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive("../base.apk" to byteArrayOf(1))))
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive("manifest.json" to byteArrayOf(1))))
        }
    }

    @Test fun rejectsTamperedApkEvenWithValidZipCrc() {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(exported())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val data = zip.readBytes()
                entries.add(entry.name to if (entry.name == "base.apk")
                    byteArrayOf(9, 2, 3, 4) else data)
            }
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive(*entries.toTypedArray())))
        }
    }

    @Test fun rejectsForgedManifestAndReorderedEntries() {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(exported())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val data = zip.readBytes()
                entries.add(entry.name to if (entry.name == "manifest.json")
                    String(data).replace("com.example.sample", "com/example/sample").toByteArray() else data)
            }
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive(*entries.toTypedArray())))
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive(*entries.reversed().toTypedArray())))
        }
    }

    @Test fun rejectsManifestLargerThan16KiBWithoutLoadingEntireArchive() {
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive(
                "base.apk" to byteArrayOf(1),
                "manifest.json" to ByteArray(17_000) { 65 },
            )))
        }
    }

    @Test fun baseOnlyExportAlsoVerifies() {
        val output = ByteArrayOutputStream()
        InstalledApkBundleExport.write(
            "com.example.single",
            listOf(InstalledApkBundleExport.Source("base.apk", 3) {
                ByteArrayInputStream(byteArrayOf(1, 2, 3))
            }), output,
        )
        val verified = InstalledApkBundleVerifier.verify(ByteArrayInputStream(output.toByteArray()))
        assertEquals("com.example.single", verified.packageName)
        assertEquals(1, verified.entries.size)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or
            ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 3].toInt() and 255) shl 24)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 255).toByte()
        bytes[offset + 1] = ((value ushr 8) and 255).toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        repeat(4) { index ->
            bytes[offset + index] = ((value ushr (index * 8)) and 255L).toByte()
        }
    }

    private fun exportedWithFirstCentralUnixType(mode: Int): ByteArray {
        val bytes = exported()
        val centralOffset = readU32(bytes, bytes.size - 22 + 16)
        val nameLength = (bytes[centralOffset + 28].toInt() and 255) or
            ((bytes[centralOffset + 29].toInt() and 255) shl 8)
        val name = String(bytes, centralOffset + 46, nameLength, Charsets.UTF_8)
        assertEquals("base.apk", name)
        val versionMadeBy = (bytes[centralOffset + 4].toInt() and 255) or
            ((bytes[centralOffset + 5].toInt() and 255) shl 8)
        writeU16(bytes, centralOffset + 4, (versionMadeBy and 255) or (3 shl 8))
        writeU32(bytes, centralOffset + 38, (mode.toLong() and 0xffffL) shl 16)
        return bytes
    }

    @Test fun rejectsTruncatedCentralDirectoryEvenWhenLocalApkHashesMatch() {
        val valid = exported()
        val centralOffset = readU32(valid, valid.size - 22 + 16)
        for (truncated in listOf(
            valid.copyOf(centralOffset),
            valid.copyOf(valid.size - 22),
            valid.copyOf(valid.size - 7),
        )) {
            assertThrows(IOException::class.java) {
                InstalledApkBundleVerifier.verify(ByteArrayInputStream(truncated))
            }
        }
    }

    @Test fun rejectsTrailingGarbageAndForgedCentralEntryCount() {
        val valid = exported()
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(
                ByteArrayInputStream(valid + byteArrayOf(0, 1, 2, 3)),
            )
        }
        val forged = valid.copyOf()
        forged[forged.size - 22 + 10] = 1
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
        }
    }

    @Test fun rejectsLocalAndCentralGeneralPurposeFlagMismatch() {
        val forged = exported()
        val originalFlags = (forged[6].toInt() and 255) or
            ((forged[7].toInt() and 255) shl 8)
        assertTrue("canonical exporter uses a data descriptor", originalFlags and 0x0008 != 0)
        val changedFlags = originalFlags xor 0x0800 // toggle only the UTF-8 name flag
        forged[6] = (changedFlags and 255).toByte()
        forged[7] = ((changedFlags ushr 8) and 255).toByte()

        val failure = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
        }
        assertTrue(
            "must reject a local/central metadata split-brain",
            failure.message.orEmpty().contains("local/central"),
        )
    }

    @Test fun rejectsCentralDirectoryExtraFieldMismatchWithLocalEntry() {
        val forged = exported()
        val centralOffset = readU32(forged, forged.size - 22 + 16)
        val nameLength = (forged[centralOffset + 28].toInt() and 255) or
            ((forged[centralOffset + 29].toInt() and 255) shl 8)
        val extraLength = (forged[centralOffset + 30].toInt() and 255) or
            ((forged[centralOffset + 31].toInt() and 255) shl 8)
        assertTrue("canonical exporter should include its timestamp extra field", extraLength > 0)
        val extraStart = centralOffset + 46 + nameLength
        forged[extraStart + extraLength - 1] =
            (forged[extraStart + extraLength - 1].toInt() xor 1).toByte()

        val failure = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
        }
        assertTrue(failure.message.orEmpty().contains("extra fields differ"))
    }

    private fun exportedWithMatchingUnknownExtraField(): ByteArray {
        val bytes = exported()
        val localNameLength = (bytes[26].toInt() and 255) or
            ((bytes[27].toInt() and 255) shl 8)
        val localExtraLength = (bytes[28].toInt() and 255) or
            ((bytes[29].toInt() and 255) shl 8)
        assertEquals("canonical local timestamp extra length", 9, localExtraLength)
        val localExtraStart = 30 + localNameLength

        val centralOffset = readU32(bytes, bytes.size - 22 + 16)
        val centralNameLength = (bytes[centralOffset + 28].toInt() and 255) or
            ((bytes[centralOffset + 29].toInt() and 255) shl 8)
        val centralExtraLength = (bytes[centralOffset + 30].toInt() and 255) or
            ((bytes[centralOffset + 31].toInt() and 255) shl 8)
        assertEquals(localExtraLength, centralExtraLength)
        val centralExtraStart = centralOffset + 46 + centralNameLength

        // Keep both header copies byte-identical and structurally sized, but
        // replace the only permitted UT timestamp ID with an unknown field ID.
        writeU16(bytes, localExtraStart, 0x9999)
        writeU16(bytes, centralExtraStart, 0x9999)
        return bytes
    }

    @Test fun rejectsUnknownExtraFieldEvenWhenLocalAndCentralMatch() {
        val failure = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(
                ByteArrayInputStream(exportedWithMatchingUnknownExtraField()),
            )
        }
        assertTrue(
            "matching unknown extras must still be rejected as non-canonical",
            failure.message.orEmpty().contains("Unsupported ZIP local extra fields"),
        )
    }

    @Test fun acceptsUnixRegularFileTypeInCanonicalBundle() {
        val result = InstalledApkBundleVerifier.verify(
            ByteArrayInputStream(exportedWithFirstCentralUnixType(0x8000 or 0x01a4)),
        )
        assertEquals("com.example.sample", result.packageName)
    }

    @Test fun rejectsUnixSymlinkAndSpecialTypeInCentralDirectory() {
        val symlink = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(
                ByteArrayInputStream(exportedWithFirstCentralUnixType(0xa000 or 0x01ff)),
            )
        }
        assertTrue(symlink.message.orEmpty().contains("symbolic-link"))

        val fifo = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(
                ByteArrayInputStream(exportedWithFirstCentralUnixType(0x1000 or 0x01a4)),
            )
        }
        assertTrue(fifo.message.orEmpty().contains("special filesystem"))
    }

    @Test fun rejectsUnixDirectoryTypeForBaseApkPath() {
        val failure = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(
                ByteArrayInputStream(exportedWithFirstCentralUnixType(0x4000 or 0x01ed)),
            )
        }
        assertTrue(failure.message.orEmpty().contains("directory type lacks directory path"))
    }

    @Test fun rejectsCentralDirectoryEntryNameCrcAndLocalOffsetTampering() {
        val valid = exported()
        val centralOffset = readU32(valid, valid.size - 22 + 16)
        for (index in listOf(centralOffset + 46, centralOffset + 16, centralOffset + 42)) {
            val forged = valid.copyOf()
            forged[index] = (forged[index].toInt() xor 1).toByte()
            assertThrows(IOException::class.java) {
                InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
            }
        }
    }
    @Test fun rejectsMalformedUtf8ManifestInsteadOfSilentlyReplacingBytes() {
        val malformed = archive(
            "base.apk" to ByteArray(64) { (it * 13).toByte() },
            "manifest.json" to byteArrayOf(0xC3.toByte(), 0x28),
        )
        val failure = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(malformed))
        }
        assertTrue(failure.message.orEmpty().contains("UTF-8"))
    }

    @Test fun strictUtf8DecoderRejectsMalformedZipNameBytes() {
        val failure = assertThrows(IOException::class.java) {
            decodeStrictUtf8(byteArrayOf(0xC3.toByte(), 0x28), "ZIP entry name")
        }
        assertTrue(failure.message.orEmpty().contains("UTF-8"))
    }

    @Test fun rejectsMalformedUtf8CentralDirectoryNameBeforeCanonicalComparison() {
        val forged = exported()
        val centralOffset = readU32(forged, forged.size - 22 + 16)
        val nameStart = centralOffset + 46
        forged[nameStart] = 0xC3.toByte()
        forged[nameStart + 1] = 0x28

        val failure = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
        }
        assertTrue(failure.message.orEmpty().contains("UTF-8"))
    }

}
