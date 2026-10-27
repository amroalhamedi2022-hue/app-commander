package com.appcommander.mobile.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSignerSummaryTest {
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun singleSignerWithoutRotationKeepsCurrentFingerprintAndNoHistory() {
        val current = byteArrayOf(1, 2, 3)
        val snapshot = ApkSignerSummary.from(
            currentCertificates = listOf(current),
            historyCertificates = listOf(current),
            hasMultipleSigners = false,
            hasPastSigningCertificates = false,
        )
        assertEquals(listOf(sha(current)), snapshot.currentSha256)
        assertTrue(snapshot.signingHistorySha256.isEmpty())
        assertEquals(false, snapshot.hasMultipleSigners)
        assertEquals(false, snapshot.hasPastSigningCertificates)
    }

    @Test fun rotationHistoryPreservesPlatformOrderFromOldestToCurrent() {
        val oldest = byteArrayOf(10, 11)
        val current = byteArrayOf(20, 21)
        val snapshot = ApkSignerSummary.from(
            currentCertificates = listOf(current),
            historyCertificates = listOf(oldest, current),
            hasMultipleSigners = false,
            hasPastSigningCertificates = true,
        )
        assertEquals(listOf(sha(current)), snapshot.currentSha256)
        assertEquals(listOf(sha(oldest), sha(current)), snapshot.signingHistorySha256)
    }

    @Test fun multipleCurrentSignersAreSortedAndNeverPresentedAsRotationHistory() {
        val signerA = byteArrayOf(1, 9, 2)
        val signerB = byteArrayOf(8, 3, 7)
        val snapshot = ApkSignerSummary.from(
            currentCertificates = listOf(signerB, signerA),
            historyCertificates = null,
            hasMultipleSigners = true,
            hasPastSigningCertificates = false,
        )
        assertEquals(listOf(sha(signerA), sha(signerB)).sorted(), snapshot.currentSha256)
        assertTrue(snapshot.signingHistorySha256.isEmpty())
        assertEquals(true, snapshot.hasMultipleSigners)
    }

    @Test fun unavailableSigningMetadataRemainsUnknownInsteadOfImplyingUnsigned() {
        val snapshot = ApkSignerSummary.from(null, null, null, null)
        assertTrue(snapshot.currentSha256.isEmpty())
        assertTrue(snapshot.signingHistorySha256.isEmpty())
        assertNull(snapshot.hasMultipleSigners)
        assertNull(snapshot.hasPastSigningCertificates)
    }

    @Test fun missingHistoryBytesDoNotEraseKnownCurrentSignerOrRotationFlag() {
        val current = byteArrayOf(42)
        val snapshot = ApkSignerSummary.from(
            currentCertificates = listOf(current),
            historyCertificates = null,
            hasMultipleSigners = false,
            hasPastSigningCertificates = true,
        )
        assertEquals(listOf(sha(current)), snapshot.currentSha256)
        assertTrue(snapshot.signingHistorySha256.isEmpty())
        assertEquals(true, snapshot.hasPastSigningCertificates)
    }
}
