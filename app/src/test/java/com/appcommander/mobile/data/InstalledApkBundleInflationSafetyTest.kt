package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/**
 * The verifier accepts only level-zero App Commander ZIP exports. Test both
 * an early streaming inflation guard and the final canonical central index:
 * digest-valid malicious compressed archives must not be accepted as backups.
 */
class InstalledApkBundleInflationSafetyTest {
    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun craftedBundle(
        payloads: List<ByteArray>,
        compressedEntry: Int = -1,
        compressManifest: Boolean = false,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val names = payloads.indices.map { index ->
            if (index == 0) "base.apk" else "split_${index.toString().padStart(3, '0')}.apk"
        }
        val manifest = buildString {
            append("{\"format\":\"app-commander-apk-bundle-v1\",")
            append("\"packageName\":\"com.example.inflation\",")
            append("\"containsAppData\":false,\"signatureVerified\":false,")
            append("\"entries\":[")
            payloads.forEachIndexed { index, bytes ->
                if (index != 0) append(',')
                append("{\"name\":\"").append(names[index]).append("\",")
                append("\"size\":").append(bytes.size).append(',')
                append("\"sha256\":\"").append(digest(bytes)).append("\"}")
            }
            append("]}")
        }
        ZipOutputStream(output).use { zip ->
            payloads.forEachIndexed { index, bytes ->
                zip.setLevel(if (index == compressedEntry) 9 else 0)
                zip.putNextEntry(ZipEntry(names[index]).apply { time = 0L })
                zip.write(bytes)
                zip.closeEntry()
            }
            zip.setLevel(if (compressManifest) 9 else 0)
            zip.putNextEntry(ZipEntry("manifest.json").apply { time = 0L })
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    @Test fun acceptsRealLevelZeroExportWithMultiMegabyteBaseAndSplit() {
        val base = ByteArray(2 * 1024 * 1024) { index ->
            ((index * 31 + (index ushr 9) * 7) and 255).toByte()
        }
        val split = ByteArray(128 * 1024) { index -> (index and 255).toByte() }
        val output = ByteArrayOutputStream()
        val sources = listOf(base, split).mapIndexed { index, bytes ->
            InstalledApkBundleExport.Source(
                if (index == 0) "base.apk" else "split_001.apk",
                bytes.size.toLong(),
            ) { ByteArrayInputStream(bytes) }
        }
        val written = InstalledApkBundleExport.write("com.example.inflation", sources, output)
        val verified = InstalledApkBundleVerifier.verify(ByteArrayInputStream(output.toByteArray()))
        assertEquals(written.entries, verified.entries)
        assertEquals(written.totalApkBytes, verified.totalApkBytes)
    }

    @Test fun rejectsHighlyCompressedBaseDuringStreamingNotOnlyAtCentralDirectory() {
        val archive = craftedBundle(listOf(ByteArray(3 * 1024 * 1024)), compressedEntry = 0)
        val error = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive))
        }
        assertTrue(error.message.orEmpty().contains("expands beyond canonical ZIP budget"))
    }

    @Test fun rejectsCompressedLaterSplitWithoutBorrowingEarlierRawByteBudget() {
        val base = ByteArray(2 * 1024 * 1024) { index -> (index and 255).toByte() }
        val compressedSplit = ByteArray(3 * 1024 * 1024)
        val archive = craftedBundle(listOf(base, compressedSplit), compressedEntry = 1)
        val error = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive))
        }
        assertTrue(error.message.orEmpty().contains("expands beyond canonical ZIP budget"))
    }

    @Test fun rejectsNoncanonicalCompressedManifestEvenWithValidApkDigest() {
        val archive = craftedBundle(
            listOf(ByteArray(2048) { index -> index.toByte() }),
            compressManifest = true,
        )
        val error = assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(archive))
        }
        assertTrue(error.message.orEmpty().contains("Unsupported ZIP central directory metadata"))
    }
}
