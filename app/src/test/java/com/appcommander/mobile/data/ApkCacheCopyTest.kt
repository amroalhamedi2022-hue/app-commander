package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkCacheCopyTest {
    @Test fun copiesBinaryContentWithoutTruncation() {
        val source = ByteArray(163_841) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(source.size.toLong(), ApkCacheCopy.copy(ByteArrayInputStream(source), output))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test fun acceptsExactLimitButRejectsOversize() {
        assertEquals(3L, ApkCacheCopy.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3)), ByteArrayOutputStream(), 3))
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), ByteArrayOutputStream(), 3)
        }
    }

    @Test fun rejectsEmptyAndInvalidLimits() {
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        }
        assertThrows(IllegalArgumentException::class.java) {
            ApkCacheCopy.copy(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), 0)
        }
    }

    @Test fun toleratesShortZeroReadThenRejectsPersistentStall() {
        var calls = 0
        val transient = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                calls++
                if (calls <= 2) return 0
                if (calls == 3) { b[off] = 0x55; return 1 }
                return -1
            }
        }
        assertEquals(1L, ApkCacheCopy.copy(transient, ByteArrayOutputStream()))
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copy(stalled, ByteArrayOutputStream())
        }
    }

    @Test fun removesCachedApkOnSuccessAndParserFailure() {
        val directory = Files.createTempDirectory("apk-inspection-test").toFile()
        try {
            lateinit var cached: java.io.File
            assertEquals(7, ApkCacheCopy.withTemporaryApk(directory) { file ->
                cached = file
                file.writeText("example")
                7
            })
            assertFalse(cached.exists())
            assertThrows(IllegalStateException::class.java) {
                ApkCacheCopy.withTemporaryApk(directory) { file ->
                    cached = file
                    file.writeText("partial")
                    throw IllegalStateException("invalid APK")
                }
            }
            assertFalse(cached.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun reportsTemporaryApkCleanupFailureAfterSuccessfulInspection() {
        val directory = Files.createTempDirectory("apk-inspection-cleanup-failure").toFile()
        var cached: java.io.File? = null
        try {
            val failure = assertThrows(ApkCacheCopy.TemporaryApkCleanupDiagnostic::class.java) {
                ApkCacheCopy.withTemporaryApk(
                    directory,
                    deleteTemporary = { file -> cached = file; false },
                ) { file ->
                    file.writeText("temporary APK bytes")
                    "inspected"
                }
            }
            assertTrue(cached!!.exists())
            assertTrue(failure.message.orEmpty().contains("could not be removed"))
        } finally {
            cached?.delete()
            directory.deleteRecursively()
        }
    }

    @Test fun cleanupFailureIsSuppressedWithoutReplacingOriginalInspectionFailure() {
        val directory = Files.createTempDirectory("apk-inspection-primary-failure").toFile()
        val original = IllegalStateException("invalid APK")
        var cached: java.io.File? = null
        try {
            val thrown = assertThrows(IllegalStateException::class.java) {
                ApkCacheCopy.withTemporaryApk(
                    directory,
                    deleteTemporary = { file -> cached = file; false },
                ) { file ->
                    file.writeText("partial APK")
                    throw original
                }
            }
            assertSame(original, thrown)
            assertEquals(1, thrown.suppressed.size)
            assertTrue(thrown.suppressed.single() is ApkCacheCopy.TemporaryApkCleanupDiagnostic)
            assertTrue(cached!!.exists())
        } finally {
            cached?.delete()
            directory.deleteRecursively()
        }
    }

    @Test fun cleanupExceptionIsReportedWithCauseWhenInspectionSucceeded() {
        val directory = Files.createTempDirectory("apk-inspection-cleanup-exception").toFile()
        val cleanupFailure = SecurityException("delete denied")
        var cached: java.io.File? = null
        try {
            val thrown = assertThrows(ApkCacheCopy.TemporaryApkCleanupDiagnostic::class.java) {
                ApkCacheCopy.withTemporaryApk(
                    directory,
                    deleteTemporary = { file -> cached = file; throw cleanupFailure },
                ) { file ->
                    file.writeText("temporary APK")
                    Unit
                }
            }
            assertSame(cleanupFailure, thrown.cause)
            assertTrue(cached!!.exists())
        } finally {
            cached?.delete()
            directory.deleteRecursively()
        }
    }

    @Test fun propagatesProviderFailure() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("provider crashed")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("provider crashed")
        }
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copy(broken, ByteArrayOutputStream())
        }
    }
    @Test fun hashesExactCopiedBytesInSamePass() {
        val bytes = "abc".toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        val result = ApkCacheCopy.copyWithSha256(ByteArrayInputStream(bytes), output)
        assertEquals(3L, result.sizeBytes)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
    }

    @Test fun hashesBinaryAcrossBufferBoundariesAndTransientEmptyReads() {
        val bytes = ByteArray(131_079) { ((it * 17) % 256).toByte() }
        val input = object : InputStream() {
            private var offset = 0
            private var first = true
            override fun read(): Int = if (offset < bytes.size) bytes[offset++].toInt() and 0xff else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) { first = false; return 0 }
                if (offset == bytes.size) return -1
                val count = minOf(len, 4093, bytes.size - offset)
                System.arraycopy(bytes, offset, b, off, count)
                offset += count
                return count
            }
        }
        val output = ByteArrayOutputStream()
        val result = ApkCacheCopy.copyWithSha256(input, output)
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(bytes.size.toLong(), result.sizeBytes)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(expected, result.sha256)
    }

    @Test fun copyWithSha256DoesNotReturnDigestForOversizedOrFailedWrites() {
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copyWithSha256(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), ByteArrayOutputStream(), 3)
        }
        val broken = object : java.io.OutputStream() {
            override fun write(b: Int) { throw IOException("cache full") }
            override fun write(b: ByteArray, off: Int, len: Int) { throw IOException("cache full") }
        }
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copyWithSha256(ByteArrayInputStream(byteArrayOf(1, 2, 3)), broken)
        }
    }


    @Test fun cancellationIsPropagatedBetweenCopyChunksWithoutReadingTheRemainder() {
        val bytes = ByteArray(3 * 64 * 1024) { (it * 7).toByte() }
        val output = ByteArrayOutputStream()
        val cancelled = CancellationException("user cancelled export")
        var checks = 0
        val thrown = assertThrows(CancellationException::class.java) {
            ApkCacheCopy.copyWithSha256(
                ByteArrayInputStream(bytes), output,
                checkCancelled = {
                    checks++
                    if (checks == 2) throw cancelled
                },
            )
        }
        assertSame(cancelled, thrown)
        assertEquals("only the first 64 KiB chunk should have been copied", 64 * 1024, output.size())
    }

}
