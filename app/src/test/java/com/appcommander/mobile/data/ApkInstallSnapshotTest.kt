package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkInstallSnapshotTest {
    @get:Rule val temp = TemporaryFolder()

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test fun createsByteExactPrivateSnapshotForInstaller() {
        val bytes = ByteArray(131_079) { (it * 37).toByte() }
        val directory = temp.newFolder("install-snapshots")
        val file = ApkInstallSnapshot.create(
            directory, ByteArrayInputStream(bytes), bytes.size.toLong(), sha(bytes).uppercase(),
        )
        assertNotNull(file)
        assertEquals(directory.canonicalFile, file!!.parentFile.canonicalFile)
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(file.name.startsWith("verified-"))
        assertTrue(file.name.endsWith(".apk"))
    }

    @Test fun refusesProviderSwapOfEqualSizeAndRemovesPartialCopy() {
        val original = "original package bytes".toByteArray()
        val swapped = original.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        val directory = temp.newFolder("mismatch")
        assertNull(ApkInstallSnapshot.create(
            directory, ByteArrayInputStream(swapped), original.size.toLong(), sha(original),
        ))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun rejectsOversizedStreamBeforeWritingBeyondInspectedLength() {
        val original = byteArrayOf(1, 2, 3)
        val directory = temp.newFolder("oversize")
        assertThrows(IOException::class.java) {
            ApkInstallSnapshot.create(
                directory, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                original.size.toLong(), sha(original),
            )
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun removesSnapshotIfSafReadThrows() {
        val directory = temp.newFolder("provider-failure")
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("SAF provider failed")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("SAF provider failed")
        }
        assertThrows(IOException::class.java) {
            ApkInstallSnapshot.create(directory, broken, 1, sha(byteArrayOf(1)))
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun invalidInspectionMetadataCannotCreateSnapshot() {
        val directory = temp.newFolder("invalid")
        val bytes = byteArrayOf(7)
        assertNull(ApkInstallSnapshot.create(directory, ByteArrayInputStream(bytes), 0, sha(bytes)))
        assertNull(ApkInstallSnapshot.create(directory, ByteArrayInputStream(bytes), 1, "bad"))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun expiryOnlyDeletesOldVerifiedSnapshots() {
        val directory = temp.newFolder("expiry")
        val now = 1_900_000_000_000L
        val old = directory.resolve("verified-old.apk").apply { writeText("old") }
        val fresh = directory.resolve("verified-fresh.apk").apply { writeText("new") }
        val unrelated = directory.resolve("other-cache.apk").apply { writeText("keep") }
        assertTrue(old.setLastModified(now - ApkInstallSnapshot.MAX_AGE_MILLIS - 1))
        assertTrue(fresh.setLastModified(now))
        assertTrue(unrelated.setLastModified(now - ApkInstallSnapshot.MAX_AGE_MILLIS - 1))
        assertEquals(1, ApkInstallSnapshot.cleanupExpired(directory, now))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(unrelated.exists())
    }
}
