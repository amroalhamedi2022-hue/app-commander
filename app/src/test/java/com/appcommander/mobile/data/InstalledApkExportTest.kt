package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class InstalledApkExportTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun copiesInstalledBaseApkExactlyWithChecksum() {
        val bytes = ByteArray(131_079) { (it * 29).toByte() }
        val output = ByteArrayOutputStream()
        val result = InstalledApkExport.copyBase(
            ByteArrayInputStream(bytes), output, bytes.size.toLong(), hasSplits = false,
        )
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), result.bytes)
        assertEquals(sha(bytes), result.sha256)
        assertFalse(result.hasSplits)
    }

    @Test fun splitInstallIsExplicitlyMarkedIncomplete() {
        val bytes = byteArrayOf(1, 2, 3)
        val result = InstalledApkExport.copyBase(
            ByteArrayInputStream(bytes), ByteArrayOutputStream(), 3, hasSplits = true,
        )
        assertTrue(result.hasSplits)
    }

    @Test fun rejectsTruncatedAndOversizedSources() {
        assertThrows(IOException::class.java) {
            InstalledApkExport.copyBase(
                ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), 3, false,
            )
        }
        assertThrows(IOException::class.java) {
            InstalledApkExport.copyBase(
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), ByteArrayOutputStream(), 3, false,
            )
        }
    }

    @Test fun rejectsInvalidDeclaredSizeBeforeWriting() {
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) {
            InstalledApkExport.copyBase(
                ByteArrayInputStream(byteArrayOf(1)), output, 0, false,
            )
        }
        assertThrows(IOException::class.java) {
            InstalledApkExport.copyBase(
                ByteArrayInputStream(byteArrayOf(1)), output,
                ApkCacheCopy.MAX_APK_BYTES + 1, false,
            )
        }
        assertEquals(0, output.size())
    }
}
