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
        assertEquals(sha(bytes), result.sha256)
        assertEquals(3L, result.bytes)
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

    @Test fun successfulExportNeverDeletesNewDocument() {
        var deletes = 0
        val result = InstalledApkExport.withPartialDocumentCleanup(
            cleanup = { deletes++ },
            write = { "saved" },
        )
        assertEquals("saved", result)
        assertEquals(0, deletes)
    }

    @Test fun failedExportAttemptsCleanupAndPreservesOriginalError() {
        var deletes = 0
        val original = IOException("source truncated")
        val thrown = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { deletes++ },
                write = { throw original },
            )
        }
        assertSame(original, thrown)
        assertEquals(1, deletes)
    }

    @Test fun cleanupFailureCannotHideOriginalExportFailure() {
        val original = IOException("provider write failed")
        val thrown = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { throw SecurityException("provider denied deletion") },
                write = { throw original },
            )
        }
        assertSame(original, thrown)
    }

    @Test fun baseSafExportReadbackAfterCloseChecksBytesAndSplitWarning() {
        val base = ByteArray(131_079) { (it * 31).toByte() }
        val saved = ByteArrayOutputStream()
        var outputClosed = false
        var readCount = 0
        val result = InstalledApkExport.copyBaseAndVerify(
            openSource = { ByteArrayInputStream(base) },
            openForWrite = {
                object : java.io.OutputStream() {
                    override fun write(b: Int) { saved.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) { saved.write(b, off, len) }
                    override fun close() { outputClosed = true }
                }
            },
            openForRead = {
                assertTrue("SAF output must close before readback", outputClosed)
                readCount++
                ByteArrayInputStream(saved.toByteArray())
            },
            expectedBytes = base.size.toLong(),
            hasSplits = true,
        )
        assertEquals(1, readCount)
        assertTrue(result.hasSplits)
        assertEquals(base.size.toLong(), result.bytes)
        assertEquals(sha(base), result.sha256)
        assertArrayEquals(base, saved.toByteArray())
    }

    @Test fun baseSafExportRejectsSilentProviderTruncationOrSameSizeMutation() {
        val base = byteArrayOf(1, 2, 3, 4, 5)
        for (truncate in listOf(true, false)) {
            val saved = ByteArrayOutputStream()
            assertThrows(IOException::class.java) {
                InstalledApkExport.copyBaseAndVerify(
                    openSource = { ByteArrayInputStream(base) },
                    openForWrite = { saved },
                    openForRead = {
                        val bytes = saved.toByteArray()
                        if (truncate) ByteArrayInputStream(bytes.copyOf(bytes.size - 1))
                        else ByteArrayInputStream(bytes.apply { this[2] = 99 })
                    },
                    expectedBytes = base.size.toLong(),
                    hasSplits = false,
                )
            }
        }
    }

    @Test fun baseSafExportReadbackDeniedTriggersCleanupWithoutMaskingFailure() {
        val base = byteArrayOf(7, 8, 9)
        var deletes = 0
        val error = assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(
                cleanup = { deletes++ },
                write = {
                    InstalledApkExport.copyBaseAndVerify(
                        openSource = { ByteArrayInputStream(base) },
                        openForWrite = { ByteArrayOutputStream() },
                        openForRead = { throw IOException("Provider does not allow reading") },
                        expectedBytes = 3,
                        hasSplits = false,
                    )
                },
            )
        }
        assertEquals("Provider does not allow reading", error.message)
        assertEquals(1, deletes)
    }

    @Test fun baseSafExportRejectsInvalidSizeBeforeOpeningStreams() {
        var opens = 0
        for (size in listOf(0L, ApkCacheCopy.MAX_APK_BYTES + 1)) {
            assertThrows(IOException::class.java) {
                InstalledApkExport.copyBaseAndVerify(
                    openSource = { opens++; ByteArrayInputStream(byteArrayOf(1)) },
                    openForWrite = { opens++; ByteArrayOutputStream() },
                    openForRead = { opens++; ByteArrayInputStream(byteArrayOf(1)) },
                    expectedBytes = size,
                    hasSplits = false,
                )
            }
        }
        assertEquals(0, opens)
    }

    @Test fun baseSafExportDoesNotReadBackWhenProviderWriteFails() {
        var readback = false
        assertThrows(IOException::class.java) {
            InstalledApkExport.copyBaseAndVerify(
                openSource = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
                openForWrite = {
                    object : java.io.OutputStream() {
                        override fun write(b: Int) { throw IOException("SAF disk full") }
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            throw IOException("SAF disk full")
                        }
                    }
                },
                openForRead = {
                    readback = true
                    ByteArrayInputStream(byteArrayOf(1, 2, 3))
                },
                expectedBytes = 3,
                hasSplits = false,
            )
        }
        assertFalse(readback)
    }

    @Test fun baseSafExportRejectsStalledProviderReadback() {
        val base = byteArrayOf(4, 5)
        assertThrows(IOException::class.java) {
            InstalledApkExport.copyBaseAndVerify(
                openSource = { ByteArrayInputStream(base) },
                openForWrite = { ByteArrayOutputStream() },
                openForRead = {
                    object : java.io.InputStream() {
                        override fun read(): Int = 0
                        override fun read(b: ByteArray, off: Int, len: Int): Int = 0
                    }
                },
                expectedBytes = 2,
                hasSplits = false,
            )
        }
    }
}
