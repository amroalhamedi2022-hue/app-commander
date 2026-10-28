package com.appcommander.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApkInspectionSignalsTest {
    @Test fun networkAndBootPermissionsAreVisibleAsManifestReviewPromptsOnly() {
        val findings = ApkInspectionSignals.evaluate(
            targetSdk = 35,
            permissions = listOf(
                "android.permission.INTERNET",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.INTERNET",
            ),
            signerSha256 = listOf("certificate"),
        )
        val byCode = findings.associateBy { it.code }
        assertEquals(2, findings.size)
        assertTrue(byCode.containsKey("android.permission.INTERNET"))
        assertTrue(byCode.containsKey("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(byCode.getValue("android.permission.INTERNET").description.contains("لا يثبت"))
        assertTrue(byCode.getValue("android.permission.RECEIVE_BOOT_COMPLETED").description.contains("تحقّق"))
    }

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
    @Test fun sensitivePermissionsAreExplainedWithoutClaimingTheyWereGranted() {
        val findings = ApkInspectionSignals.evaluate(
            35,
            listOf(
                "android.permission.CAMERA",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.SEND_SMS",
                "android.permission.READ_SMS",
                "android.permission.READ_CALL_LOG",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.CAMERA",
            ),
            listOf("certificate"),
        )
        val codes = findings.map { it.code }
        assertTrue(codes.contains("android.permission.CAMERA"))
        assertTrue(codes.contains("android.permission.READ_CALL_LOG"))
        assertTrue(codes.contains("android.permission.READ_MEDIA_IMAGES"))
        assertEquals(1, codes.count { it == "android.permission.CAMERA" })
        assertTrue(codes.contains("sms_read_send_combination"))
        assertTrue(codes.contains("precise_background_location_combination"))
        assertFalse(findings.any { it.description.contains("تم منح") })
    }

    @Test fun combinationsRequireBothExactPermissionNames() {
        val codes = ApkInspectionSignals.evaluate(
            35,
            listOf("android.permission.SEND_SMS", "android.permission.ACCESS_BACKGROUND_LOCATION"),
            listOf("certificate"),
        ).map { it.code }
        assertFalse(codes.contains("sms_read_send_combination"))
        assertFalse(codes.contains("precise_background_location_combination"))
    }

    @Test fun exportedComponentsWithoutDeclaredPermissionsProduceReviewPrompts() {
        val findings = ApkInspectionSignals.evaluate(
            targetSdk = 35,
            permissions = emptyList(),
            signerSha256 = listOf("certificate"),
            unguardedExportedActivities = 2,
            unguardedExportedServices = 1,
            unguardedExportedReceivers = 3,
            exportedProviders = 4,
            providersWithoutReadPermission = 1,
            providersWithoutWritePermission = 2,
            providerPathRulesWithoutReadPermission = 2,
            providerPathRulesWithoutWritePermission = 1,
        ).associateBy { it.code }

        val components = findings["exported_components_without_declared_permission"]
        assertNotNull(components)
        assertTrue(components!!.description.contains("6"))
        assertTrue(components.description.contains("Manifest فقط"))

        val providers = findings["exported_provider_permission_review"]
        assertNotNull(providers)
        assertTrue(providers!!.description.contains("4 ContentProvider"))
        assertTrue(providers.description.contains("1 دون إذن قراءة"))
        assertTrue(providers.description.contains("2 دون إذن كتابة"))
        assertTrue(providers.description.contains("2 قراءة و1 كتابة"))
        assertTrue(providers.description.contains("حكمًا بثغرة"))
    }

    @Test fun protectedExportsAndZeroProviderGapsDoNotRaiseExposureFindings() {
        val codes = ApkInspectionSignals.evaluate(
            targetSdk = 35,
            permissions = emptyList(),
            signerSha256 = listOf("certificate"),
            unguardedExportedActivities = 0,
            unguardedExportedServices = 0,
            unguardedExportedReceivers = 0,
            exportedProviders = 3,
            providersWithoutReadPermission = 0,
            providersWithoutWritePermission = 0,
            providerPathRulesWithoutReadPermission = 0,
            providerPathRulesWithoutWritePermission = 0,
        ).map { it.code }
        assertFalse(codes.contains("exported_components_without_declared_permission"))
        assertFalse(codes.contains("exported_provider_permission_review"))
    }

    @Test fun negativeExposureCountsAreClampedAndCannotCreateFindings() {
        val codes = ApkInspectionSignals.evaluate(
            targetSdk = 35,
            permissions = emptyList(),
            signerSha256 = listOf("certificate"),
            unguardedExportedActivities = Int.MIN_VALUE,
            unguardedExportedServices = -1,
            unguardedExportedReceivers = -2,
            exportedProviders = -1,
            providersWithoutReadPermission = Int.MAX_VALUE,
            providersWithoutWritePermission = Int.MAX_VALUE,
            providerPathRulesWithoutReadPermission = -1,
            providerPathRulesWithoutWritePermission = -1,
            exportedComponentsUsingNonSignatureAppPermission = -1,
            providerDirectionsUsingNonSignatureAppPermission = -1,
            providerPathRulesUsingNonSignatureAppPermission = -1,
        ).map { it.code }
        assertFalse(codes.contains("exported_components_without_declared_permission"))
        assertFalse(codes.contains("exported_provider_permission_review"))
        assertFalse(codes.contains("exported_gates_with_non_signature_app_permission"))
    }
    @Test fun nonSignatureAppDefinedPermissionGatesAreReviewPromptsNotVulnerabilityClaims() {
        val finding = ApkInspectionSignals.evaluate(
            targetSdk = 35,
            permissions = emptyList(),
            signerSha256 = listOf("certificate"),
            exportedComponentsUsingNonSignatureAppPermission = 2,
            providerDirectionsUsingNonSignatureAppPermission = 3,
            providerPathRulesUsingNonSignatureAppPermission = 4,
        ).single { it.code == "exported_gates_with_non_signature_app_permission" }

        assertTrue(finding.description.contains("2 مكوّنات"))
        assertTrue(finding.description.contains("3 اتجاهات"))
        assertTrue(finding.description.contains("4 قواعد مسارات"))
        assertTrue(finding.description.contains("normal أو dangerous"))
        assertTrue(finding.description.contains("لا يثبت إمكانية الوصول الفعلية"))
    }

}
