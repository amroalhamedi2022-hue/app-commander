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
        )
        val result = ApkArchivePreflight.check(apk)
        assertEquals(3, result.entries)
        assertEquals(30L, result.declaredUncompressedBytes)
    }

    @Test fun rejectsMissingOrEmptyManifest() {
        val absent = zip("absent.apk", "classes.dex" to byteArrayOf(1))
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(absent) }
        val empty = zip("empty-manifest.apk", "AndroidManifest.xml" to byteArrayOf())
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(empty) }
    }

    @Test fun rejectsUnsafeArchivePathsWithoutExtractingAnything() {
        for ((index, path) in listOf("../escape", "/absolute", "assets/../bad", "a\\b", "bad\nname").withIndex()) {
            val apk = zip("unsafe-$index.apk",
                "AndroidManifest.xml" to byteArrayOf(1),
                path to byteArrayOf(2),
            )
            assertThrows(path, IOException::class.java) { ApkArchivePreflight.check(apk) }
        }
    }

    @Test fun rejectsInvalidOrEmptyZipBeforeAndroidParser() {
        val invalid = temp.newFile("invalid.apk").apply { writeText("not a ZIP archive") }
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(invalid) }
        val empty = temp.newFile("empty.apk")
        assertThrows(IOException::class.java) { ApkArchivePreflight.check(empty) }
    }
}
