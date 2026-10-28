package com.appcommander.mobile.data

import com.appcommander.mobile.model.ApkCertificateMetadata
import com.appcommander.mobile.model.ApkCertificateParseStatus
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale
import javax.security.auth.x500.X500Principal

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
    val currentCertificateDetails: List<ApkCertificateMetadata>,
    val signingHistoryDetails: List<ApkCertificateMetadata>,
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
            .map(ApkCertificateMetadataParser::parse)
            .distinctBy { it.sha256 }
            .sortedBy { it.sha256 }

        // SigningInfo returns null history for multiple signers. Only publish a
        // rotation chain when the platform explicitly reports past certificates;
        // preserve its oldest-to-current order exactly.
        val history = if (hasMultipleSigners == false && hasPastSigningCertificates == true) {
            historyCertificates.orEmpty().map(ApkCertificateMetadataParser::parse)
        } else {
            emptyList()
        }

        return ApkSignerSnapshot(
            currentSha256 = current.map { it.sha256 },
            signingHistorySha256 = history.map { it.sha256 },
            hasMultipleSigners = hasMultipleSigners,
            hasPastSigningCertificates = hasPastSigningCertificates,
            currentCertificateDetails = current,
            signingHistoryDetails = history,
        )
    }
}

/**
 * Bounded, best-effort X.509 metadata parser. It always preserves the raw
 * certificate fingerprint, even if certificate details cannot be decoded.
 * This is deliberately NOT an APK signature verifier.
 */
internal object ApkCertificateMetadataParser {
    internal const val MAX_CERTIFICATE_BYTES = 1_048_576

    fun parse(bytes: ByteArray): ApkCertificateMetadata {
        val fingerprint = sha256(bytes)
        if (bytes.isEmpty()) return unavailable(fingerprint, ApkCertificateParseStatus.EMPTY_INPUT)
        if (bytes.size > MAX_CERTIFICATE_BYTES) {
            return unavailable(fingerprint, ApkCertificateParseStatus.OVERSIZED)
        }

        val certificate = try {
            val input = ByteArrayInputStream(bytes)
            val parsed = CertificateFactory.getInstance("X.509")
                .generateCertificate(input) as? X509Certificate
            if (parsed == null || input.available() != 0 || !parsed.encoded.contentEquals(bytes)) null
            else parsed
        } catch (_: Exception) {
            null
        } ?: return unavailable(fingerprint, ApkCertificateParseStatus.INVALID_DER)

        return try {
            ApkCertificateMetadata(
                sha256 = fingerprint,
                subject = certificate.subjectX500Principal.getName(X500Principal.RFC2253),
                issuer = certificate.issuerX500Principal.getName(X500Principal.RFC2253),
                serialNumberHex = certificate.serialNumber.toString(16).uppercase(Locale.ROOT),
                notBeforeEpochMillis = certificate.notBefore.time,
                notAfterEpochMillis = certificate.notAfter.time,
                signatureAlgorithm = certificate.sigAlgName,
                publicKeyAlgorithm = certificate.publicKey.algorithm,
                parseStatus = ApkCertificateParseStatus.PARSED,
            )
        } catch (_: Exception) {
            unavailable(fingerprint, ApkCertificateParseStatus.INVALID_DER)
        }
    }

    private fun unavailable(
        fingerprint: String,
        status: ApkCertificateParseStatus,
    ) = ApkCertificateMetadata(
        sha256 = fingerprint,
        subject = null,
        issuer = null,
        serialNumberHex = null,
        notBeforeEpochMillis = null,
        notAfterEpochMillis = null,
        signatureAlgorithm = null,
        publicKeyAlgorithm = null,
        parseStatus = status,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
