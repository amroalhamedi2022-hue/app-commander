package com.appcommander.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInspectionSignalsTest {
    @Test fun emptyModernMetadataHasNoListedIndicatorsButNoSafetyClaim() {
        assertTrue(ApkInspectionSignals.evaluate(35, emptyList(), listOf("known-certificate-hash")).isEmpty())
        assertTrue(ApkInspectionSignals.DISCLAIMER.contains("ليست فحصًا"))
    }

    @Test fun missingSignerAndLegacyTargetAreFlagged() {
        val codes = ApkInspectionSignals.evaluate(23, emptyList(), emptyList()).map { it.code }
        assertEquals(listOf("legacy_target", "signer_unavailable"), codes)
    }

    @Test fun onlyExactRequestedPermissionsAreReportedAndDeduplicated() {
        val codes = ApkInspectionSignals.evaluate(
            35,
            listOf(
                "android.permission.READ_SMS",
                "android.permission.READ_SMS",
                "android.permission.READ_SMS_EXTRA",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "com.example.CUSTOM",
            ),
            listOf("certificate"),
        ).map { it.code }
        assertEquals(listOf("android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.READ_SMS"), codes)
        assertFalse(codes.contains("android.permission.READ_SMS_EXTRA"))
    }

    @Test fun unknownTargetAndAccessibilityIndicatorsAreNotMisclassifiedAsMalware() {
        val findings = ApkInspectionSignals.evaluate(
            0, listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"), listOf("hash"),
        )
        assertEquals("target_unknown", findings.first().code)
        assertTrue(findings.any { it.code == "android.permission.BIND_ACCESSIBILITY_SERVICE" })
        assertFalse(findings.any { it.description.contains("فيروس") })
    }
}
