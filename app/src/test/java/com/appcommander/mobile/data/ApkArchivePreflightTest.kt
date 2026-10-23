package com.appcommander.mobile.data

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
