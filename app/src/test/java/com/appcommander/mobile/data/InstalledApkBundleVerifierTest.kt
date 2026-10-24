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
}
