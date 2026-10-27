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
                zip.putNextEntry(ZipEntry(name))
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
