package com.appcommander.mobile.data

import com.appcommander.mobile.model.ApkCertificateParseStatus
import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkCertificateMetadataTest {
    private val fixture by lazy {
        Base64.getDecoder().decode(
            "MIIDWTCCAkGgAwIBAgIEAaKzxDANBgkqhkiG9w0BAQsFADBDMQswCQYDVQQGEwJVUzEbMBkGA1UECgwSQXBwIENvbW1hbmRlciBUZXN0MRcwFQYDVQQDDA5TaWduZXIgRml4dHVyZTAgFw0yNjEwMjcwNDU1NDJaGA8yMTI2MTAwMzA0NTU0MlowQzELMAkGA1UEBhMCVVMxGzAZBgNVBAoMEkFwcCBDb21tYW5kZXIgVGVzdDEXMBUGA1UEAwwOU2lnbmVyIEZpeHR1cmUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDFHv+lQ2oljcHujjYo8bWWLyVQ9EuRVnItDaLg0gTMEopqSTSWqTvCP1fm2GbKNf5h4dfQ8VMgzEswB20K4C2zTuP01Z1mzUZr3OdxoOxrqVVDuh9DbGgG4f/iygwkSR329vlsEVyVeK8slYONS5GX9IUueFEM38kw/bpQTuUcl6hmZHQfFqlEjNyhpwFVkziXl8ccqT22eaJv+pzCcWqgmRJaaY193mWuZDLtpsgvh7QVxiOwT8UWmc58jAjbeLD1rgTAmoW8kLs+t0zauqf6D61gUfeowNMUOUutKVKuXMosN6a3ltkV2zGQ1ys4iiH2BtkprnyFo2Ta6TecrHz5AgMBAAGjUzBRMB0GA1UdDgQWBBRbDeekED7jIqGuAr0D7pp18vYiejAfBgNVHSMEGDAWgBRbDeekED7jIqGuAr0D7pp18vYiejAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQA4fkozttPB27cEgv6eJSXKQY/tarEt7Sr3ymjyixULq7u8TcwcL70TwCqCKLlSO+njmAw88IRDnFuzuMEi8hdr2yc68PscHPmtpmSb6fEIWJludkqV453vLfiRRtjd7ssN8DfyrKk5c/+zWkR2r/DcqEQedsEP44HFZ2BSAfqI4I/IGyC1TZ1GufKAb39JsOktjXEeobWAd5RWr076DBnUsQpN2XXMHR2pyQOMkLMIJVVw84H3VxjRXlBG7I0DLYhzUGcII0Ll0N50F4FiwyOLWlA4WKz9Cwxo1wLB2My+KPJZWV3zMrMszZ8t1jJu2qGPta4XBdt81iLlHZ2xgBwf"
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    @Test fun parsesX509IdentityAndValidityDatesFromGeneratedFixture() {
        val details = ApkCertificateMetadataParser.parse(fixture)

        assertEquals(ApkCertificateParseStatus.PARSED, details.parseStatus)
        assertEquals(sha256(fixture), details.sha256)
        assertTrue(details.subject.orEmpty().contains("CN=Signer Fixture"))
        assertTrue(details.issuer.orEmpty().contains("CN=Signer Fixture"))
        assertEquals("1A2B3C4", details.serialNumberHex)
        assertEquals("RSA", details.publicKeyAlgorithm)
        assertTrue(details.signatureAlgorithm.orEmpty().contains("SHA256", ignoreCase = true))
        assertNotNull(details.notBeforeEpochMillis)
        assertNotNull(details.notAfterEpochMillis)
        assertTrue(details.notAfterEpochMillis!! > details.notBeforeEpochMillis!!)
    }

    @Test fun malformedDerPreservesFingerprintWithoutInventingCertificateFields() {
        val malformed = byteArrayOf(0x30, 0x01, 0x00)
        val details = ApkCertificateMetadataParser.parse(malformed)

        assertEquals(ApkCertificateParseStatus.INVALID_DER, details.parseStatus)
        assertEquals(sha256(malformed), details.sha256)
        assertNull(details.subject)
        assertNull(details.issuer)
        assertNull(details.notBeforeEpochMillis)
        assertNull(details.notAfterEpochMillis)
    }

    @Test fun emptyAndOversizedCertificatesFailClosedBeforeX509Parsing() {
        assertEquals(
            ApkCertificateParseStatus.EMPTY_INPUT,
            ApkCertificateMetadataParser.parse(byteArrayOf()).parseStatus,
        )
        val oversized = ByteArray(ApkCertificateMetadataParser.MAX_CERTIFICATE_BYTES + 1) { 0x5A }
        val details = ApkCertificateMetadataParser.parse(oversized)
        assertEquals(ApkCertificateParseStatus.OVERSIZED, details.parseStatus)
        assertEquals(sha256(oversized), details.sha256)
        assertNull(details.subject)
    }

    @Test fun signerPipelineKeepsFingerprintsAlignedAndHistoryOrderStable() {
        val malformed = byteArrayOf(0x30, 0x01, 0x00)
        val snapshot = ApkSignerSummary.from(
            currentCertificates = listOf(fixture, malformed, fixture.copyOf()),
            historyCertificates = listOf(malformed, fixture),
            hasMultipleSigners = false,
            hasPastSigningCertificates = true,
        )

        assertEquals(snapshot.currentSha256, snapshot.currentCertificateDetails.map { it.sha256 })
        assertEquals(listOf(sha256(malformed), sha256(fixture)), snapshot.signingHistorySha256)
        assertEquals(snapshot.signingHistorySha256, snapshot.signingHistoryDetails.map { it.sha256 })
        assertEquals(ApkCertificateParseStatus.INVALID_DER, snapshot.signingHistoryDetails.first().parseStatus)
        assertEquals(ApkCertificateParseStatus.PARSED, snapshot.signingHistoryDetails.last().parseStatus)
    }
}
