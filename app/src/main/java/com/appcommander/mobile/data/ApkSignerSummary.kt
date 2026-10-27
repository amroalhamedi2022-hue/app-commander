package com.appcommander.mobile.data

import java.security.MessageDigest

/**
 * Pure projection of Android SigningInfo metadata for the APK Inspector.
 * Certificate rotation order is meaningful for a single signer; multiple
 * current signers are an unordered identity set and have no single-signer
 * rotation history. These fingerprints are metadata, not a malware verdict.
 */
internal data class ApkSignerSnapshot(
    val currentSha256: List<String>,
    val signingHistorySha256: List<String>,
    val hasMultipleSigners: Boolean?,
    val hasPastSigningCertificates: Boolean?,
)

internal object ApkSignerSummary {
    fun from(
        currentCertificates: List<ByteArray>?,
        historyCertificates: List<ByteArray>?,
        hasMultipleSigners: Boolean?,
        hasPastSigningCertificates: Boolean?,
    ): ApkSignerSnapshot {
        // Android does not guarantee stable ordering for a multi-signer array.
        // Sorting fingerprints makes the displayed identity set deterministic.
        val current = currentCertificates.orEmpty()
            .map(::sha256)
            .distinct()
            .sorted()

        // SigningInfo returns null history for multiple signers. Only publish a
        // rotation chain when the platform explicitly reports past certificates;
        // preserve its oldest-to-current order exactly.
        val history = if (hasMultipleSigners == false && hasPastSigningCertificates == true) {
            historyCertificates.orEmpty().map(::sha256)
        } else {
            emptyList()
        }

        return ApkSignerSnapshot(
            currentSha256 = current,
            signingHistorySha256 = history,
            hasMultipleSigners = hasMultipleSigners,
            hasPastSigningCertificates = hasPastSigningCertificates,
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
