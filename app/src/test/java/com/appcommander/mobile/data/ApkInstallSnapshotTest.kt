package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CancellationException
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

    @Test fun providerCloseFailureAfterVerifiedCopyRemovesSnapshotAndPreservesFailure() {
        val bytes = "verified bytes".toByteArray()
        val closeFailure = IOException("provider close failed")
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream(bytes)
            override fun read(): Int = delegate.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, length)
            override fun close() { throw closeFailure }
        }
        val directory = temp.newFolder("close-failure-after-copy")

        val thrown = assertThrows(IOException::class.java) {
            ApkInstallSnapshot.createFromOwnedStream(
                directory, input, bytes.size.toLong(), sha(bytes),
            )
        }

        assertSame(closeFailure, thrown)
        assertTrue("verified bytes must be deleted if the provider stream cannot close",
            directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun cleanupFailureIsSuppressedWithoutReplacingPrimaryCancellation() {
        val directory = temp.newFolder("cleanup-diagnostic")
        val file = directory.resolve("verified-stuck.apk").apply { writeText("verified") }
        val cancellation = CancellationException("cancelled before installer handoff")

        val thrown = assertThrows(CancellationException::class.java) {
            try {
                throw cancellation
            } catch (failure: Throwable) {
                ApkInstallSnapshot.deleteAfterFailure(file, failure) {
                    throw SecurityException("simulated cache delete denial")
                }
                throw failure
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertTrue(thrown.suppressed.single() is SecurityException)
        assertTrue("the injected delete failure leaves the fixture for diagnostics", file.exists())
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

    @Test fun creatingNewSnapshotReclaimsExpiredVerifiedFilesFirst() {
        val directory = temp.newFolder("reclaim-before-copy")
        val old = directory.resolve("verified-expired.apk").apply { writeText("old") }
        val unrelated = directory.resolve("notes.txt").apply { writeText("keep") }
        val now = System.currentTimeMillis()
        assertTrue(old.setLastModified(now - ApkInstallSnapshot.MAX_AGE_MILLIS - 60_000L))
        val bytes = "new verified package".toByteArray()
        val created = ApkInstallSnapshot.create(
            directory, ByteArrayInputStream(bytes), bytes.size.toLong(), sha(bytes),
        )
        assertNotNull(created)
        assertFalse("Expired verified APK must be reclaimed before the new copy", old.exists())
        assertTrue(unrelated.exists())
        assertArrayEquals(bytes, created!!.readBytes())
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

    @Test fun cancellationDuringSnapshotCopyPropagatesAndRemovesPartialFile() {
        val expected = ByteArray(128 * 1024) { (it * 13).toByte() }
        var delivered = false
        var cancelled = false
        val input = object : InputStream() {
            override fun read(): Int = -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (delivered) return -1
                val count = minOf(length, expected.size / 2)
                System.arraycopy(expected, 0, buffer, offset, count)
                delivered = true
                cancelled = true
                return count
            }
        }
        val cancellation = CancellationException("cancelled during snapshot copy")
        val directory = temp.newFolder("cancelled-partial-copy")

        val thrown = assertThrows(CancellationException::class.java) {
            ApkInstallSnapshot.create(
                directory, input, expected.size.toLong(), sha(expected),
            ) {
                if (cancelled) throw cancellation
            }
        }

        assertSame(cancellation, thrown)
        assertTrue("partial private snapshot must be removed on cancellation", directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationAtSnapshotEofPropagatesBeforeVerifiedFileCanEscape() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        var sawEof = false
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count < 0) sawEof = true
                return count
            }
        }
        val cancellation = CancellationException("cancelled at snapshot EOF")
        val directory = temp.newFolder("cancelled-at-eof")

        val thrown = assertThrows(CancellationException::class.java) {
            ApkInstallSnapshot.create(directory, input, bytes.size.toLong(), sha(bytes)) {
                if (sawEof) throw cancellation
            }
        }

        assertSame(cancellation, thrown)
        assertTrue("cancelled snapshot must not remain installer-visible", directory.listFiles().orEmpty().isEmpty())
    }

}
