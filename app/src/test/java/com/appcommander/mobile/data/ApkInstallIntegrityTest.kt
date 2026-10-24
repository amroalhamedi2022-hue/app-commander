package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ApkInstallIntegrityTest {
    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test fun acceptsUnchangedBinarySafContentWithoutCachingItAgain() {
        val bytes = ByteArray(131_079) { ((it * 31) % 256).toByte() }
        assertTrue(ApkInstallIntegrity.matches(ByteArrayInputStream(bytes), bytes.size.toLong(), hash(bytes)))
        assertTrue(ApkInstallIntegrity.matches(ByteArrayInputStream(bytes), bytes.size.toLong(), hash(bytes).uppercase()))
    }

    @Test fun deniesChangedContentEvenWhenByteCountMatches() {
        val inspected = "original APK bytes".toByteArray()
        val replaced = inspected.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertEquals(inspected.size, replaced.size)
        assertFalse(ApkInstallIntegrity.matches(ByteArrayInputStream(replaced), inspected.size.toLong(), hash(inspected)))
        assertFalse(ApkInstallIntegrity.matches(ByteArrayInputStream(inspected), inspected.size.toLong() + 1, hash(inspected)))
    }

    @Test fun deniesInvalidOrMissingInspectionMetadata() {
        val bytes = byteArrayOf(1, 2, 3)
        assertFalse(ApkInstallIntegrity.matches(ByteArrayInputStream(bytes), 0, hash(bytes)))
        assertFalse(ApkInstallIntegrity.matches(ByteArrayInputStream(bytes), bytes.size.toLong(), "not-a-digest"))
        assertFalse(ApkInstallIntegrity.matches(ByteArrayInputStream(byteArrayOf()), 1, hash(bytes)))
    }

    @Test fun failsClosedOnProviderReadFailureOrStall() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("provider error")
            override fun read(b: ByteArray, off: Int, len: Int): Int = throw IOException("provider error")
        }
        assertFalse(ApkInstallIntegrity.matches(broken, 1, hash(byteArrayOf(1))))
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertFalse(ApkInstallIntegrity.matches(stalled, 1, hash(byteArrayOf(1))))
    }
}
