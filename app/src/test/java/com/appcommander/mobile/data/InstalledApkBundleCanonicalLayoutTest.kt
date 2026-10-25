package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression for central-index offsets that used to pass despite pointing
 * inside a different local ZIP entry. The payload hashes alone cannot catch
 * a forged central directory because ZipInputStream reads local headers.
 */
class InstalledApkBundleCanonicalLayoutTest {
    private fun bundle(apkCount: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val sources = (0 until apkCount).map { index ->
            val name = if (index == 0) "base.apk" else
                "split_${index.toString().padStart(3, '0')}.apk"
            val payload = ByteArray(65_537 + index * 997) { position ->
                ((position * 31 + index * 17) and 255).toByte()
            }
            InstalledApkBundleExport.Source(name, payload.size.toLong()) {
                ByteArrayInputStream(payload)
            }
        }
        InstalledApkBundleExport.write("com.example.layout", sources, output)
        return output.toByteArray()
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (u16(bytes, offset).toLong() or (u16(bytes, offset + 2).toLong() shl 16))

    private fun setU32(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 4) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }

    private fun centralEntries(bytes: ByteArray): List<Int> {
        val end = bytes.size - 22
        assertEquals(0x06054b50L, u32(bytes, end))
        var cursor = u32(bytes, end + 16).toInt()
        return (0 until u16(bytes, end + 10)).map {
            val start = cursor
            assertEquals(0x02014b50L, u32(bytes, start))
            cursor += 46 + u16(bytes, start + 28) + u16(bytes, start + 30) +
                u16(bytes, start + 32)
            start
        }
    }

    @Test fun canonicalBaseAndSplitLayoutsVerifyWithoutExtraction() {
        for (count in listOf(1, 2, 4)) {
            val bytes = bundle(count)
            val entries = centralEntries(bytes)
            assertEquals(count + 1, entries.size) // manifest.json is last
            val verified = InstalledApkBundleVerifier.verify(ByteArrayInputStream(bytes))
            assertEquals("com.example.layout", verified.packageName)
            assertEquals(count, verified.entries.size)
            assertEquals(count.toLong() * 65_537L + 997L * count * (count - 1) / 2,
                verified.totalApkBytes)
        }
    }

    @Test fun rejectsForgedMiddleOffsetsEvenWhenIncreasingAndHashesStillMatch() {
        val original = bundle(4)
        val entries = centralEntries(original)
        assertTrue(entries.size >= 4)
        for (index in 1 until entries.size) {
            val forged = original.copyOf()
            // Still increasing from the previous local offset (when index 1);
            // the old validator accepted offset=1 despite the real offset
            // being hundreds of bytes later.
            val previous = u32(original, entries[index - 1] + 42)
            val actual = u32(original, entries[index] + 42)
            assertTrue(actual > previous + 1)
            setU32(forged, entries[index] + 42, previous + 1)
            assertThrows(IOException::class.java) {
                InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
            }
        }
    }

    @Test fun rejectsUnsupportedCentralFlagsWithoutTrustingPayloadHashes() {
        val original = bundle(2)
        val first = centralEntries(original).first()
        val forged = original.copyOf()
        // Reserved/unknown flag bit in central index only. The local entries,
        // their CRCs, manifest and SHA-256 remain byte-for-byte unchanged.
        forged[first + 9] = (forged[first + 9].toInt() xor 0x04).toByte()
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
        }
    }

    @Test fun rejectsCentralEntryCommentsNotProducedByAppCommander() {
        val original = bundle(1)
        val first = centralEntries(original).first()
        // A central-directory comment is not emitted by our exporter.
        // Setting a nonzero comment length without adding bytes is invalid,
        // and must fail before any attempt to treat the ZIP as a backup.
        val forged = original.copyOf()
        forged[first + 32] = 1
        assertThrows(IOException::class.java) {
            InstalledApkBundleVerifier.verify(ByteArrayInputStream(forged))
        }
    }
}
