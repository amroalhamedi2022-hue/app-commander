package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test fun propagatesProviderFailure() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("provider crashed")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("provider crashed")
        }
        assertThrows(IOException::class.java) {
            ApkCacheCopy.copy(broken, ByteArrayOutputStream())
        }
    }
}
