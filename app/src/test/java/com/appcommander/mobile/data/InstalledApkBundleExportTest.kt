package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Test

class InstalledApkBundleExportTest {
    private fun source(name: String, bytes: ByteArray) =
        InstalledApkBundleExport.Source(name, bytes.size.toLong()) { ByteArrayInputStream(bytes) }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertFalse("duplicate ZIP name", files.containsKey(entry.name))
                files[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return files
    }

    @Test fun exportsBaseAndEverySplitWithHashesAndManifest() {
        val base = ByteArray(131_079) { (it * 17).toByte() }
        val split1 = ByteArray(41_003) { (it * 29).toByte() }
        val split2 = byteArrayOf(0, 1, -1, 23, 99)
        val output = ByteArrayOutputStream()
        val result = InstalledApkBundleExport.write(
            "com.example.app",
            listOf(source("base.apk", base), source("split_001.apk", split1), source("split_002.apk", split2)),
            output,
        )
        val files = unzip(output.toByteArray())
        assertEquals(setOf("base.apk", "split_001.apk", "split_002.apk", "manifest.json"), files.keys)
        assertArrayEquals(base, files["base.apk"])
        assertArrayEquals(split1, files["split_001.apk"])
        assertArrayEquals(split2, files["split_002.apk"])
        assertEquals(3, result.entries.size)
        assertEquals((base.size + split1.size + split2.size).toLong(), result.totalApkBytes)
        val manifest = String(files.getValue("manifest.json"), Charsets.UTF_8)
        assertTrue(manifest.contains("\"packageName\":\"com.example.app\""))
        assertTrue(manifest.contains("\"containsAppData\":false"))
        assertTrue(manifest.contains("\"signatureVerified\":false"))
        for ((index, bytes) in listOf(base, split1, split2).withIndex()) {
            assertEquals(sha(bytes), result.entries[index].sha256)
            assertTrue(manifest.contains("\"sha256\":\"${sha(bytes)}\""))
        }
    }

    @Test fun baseOnlyBundleStillCarriesManifest() {
        val output = ByteArrayOutputStream()
        val result = InstalledApkBundleExport.write(
            "com.example.single", listOf(source("base.apk", byteArrayOf(1, 2))), output,
        )
        assertEquals(1, result.entries.size)
        assertEquals(setOf("base.apk", "manifest.json"), unzip(output.toByteArray()).keys)
    }

    @Test fun rejectsInvalidNamesAndLimitsBeforeOpeningAnyInputOrOutput() {
        var opened = 0
        fun unopened(name: String, size: Long) = InstalledApkBundleExport.Source(name, size) {
            opened++
            ByteArrayInputStream(byteArrayOf(1))
        }
        val invalid = listOf(
            listOf(unopened("../base.apk", 1)),
            listOf(unopened("base.apk", 1), unopened("split_003.apk", 1)),
            listOf(unopened("base.apk", 0)),
            listOf(unopened("base.apk", ApkCacheCopy.MAX_APK_BYTES + 1)),
            emptyList(),
        )
        for (sources in invalid) {
            val output = ByteArrayOutputStream()
            assertThrows(IOException::class.java) {
                InstalledApkBundleExport.write("com.example.app", sources, output)
            }
            assertEquals(0, output.size())
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write(
                "bad/package", listOf(unopened("base.apk", 1)), ByteArrayOutputStream(),
            )
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write(
                "com.example.app",
                (0 until 65).map { index ->
                    unopened(if (index == 0) "base.apk" else "split_${index.toString().padStart(3, '0')}.apk", 1)
                },
                ByteArrayOutputStream(),
            )
        }
        assertEquals(0, opened)
    }

    @Test fun detectsTruncatedAndGrowingInstalledApks() {
        val truncated = InstalledApkBundleExport.Source("base.apk", 4) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        val grown = InstalledApkBundleExport.Source("base.apk", 2) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }
        for (source in listOf(truncated, grown)) {
            assertThrows(IOException::class.java) {
                InstalledApkBundleExport.write("com.example.app", listOf(source), ByteArrayOutputStream())
            }
        }
    }

    @Test fun sourceOrDestinationFailurePropagatesForSafCleanup() {
        val failingSource = InstalledApkBundleExport.Source("base.apk", 2) {
            throw IOException("source denied")
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write("com.example.app", listOf(failingSource), ByteArrayOutputStream())
        }
        val badOutput = object : OutputStream() {
            override fun write(b: Int) { throw IOException("SAF provider refused write") }
            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("SAF provider refused write")
            }
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write(
                "com.example.app", listOf(source("base.apk", byteArrayOf(1, 2, 3))), badOutput,
            )
        }
    }

    @Test fun stalledInputFailsWithoutInfiniteLoop() {
        val stalled = InstalledApkBundleExport.Source("base.apk", 1) {
            object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int = 0
            }
        }
        assertThrows(IOException::class.java) {
            InstalledApkBundleExport.write("com.example.app", listOf(stalled), ByteArrayOutputStream())
        }
    }
}
