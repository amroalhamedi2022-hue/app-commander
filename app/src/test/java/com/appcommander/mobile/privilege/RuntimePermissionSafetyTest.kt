package com.appcommander.mobile.privilege

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePermissionSafetyTest {
    private val requested = listOf("android.permission.CAMERA", "android.permission.INTERNET")

    @Test fun allowsRequestedDangerousRuntimePermission() {
        assertTrue(RuntimePermissionSafety.canToggle("android.permission.CAMERA", requested, 35, 0x01))
        // Additional protection flags do not change the dangerous base type.
        assertTrue(RuntimePermissionSafety.canToggle("android.permission.CAMERA", requested, 35, 0x101))
    }

    @Test fun rejectsNormalSignatureAndPrivilegedPermissions() {
        assertFalse(RuntimePermissionSafety.canToggle("android.permission.INTERNET", requested, 35, 0x00))
        assertFalse(RuntimePermissionSafety.canToggle("android.permission.CAMERA", requested, 35, 0x02))
        assertFalse(RuntimePermissionSafety.canToggle("android.permission.CAMERA", requested, 35, 0x12))
    }

    @Test fun rejectsUnrequestedUnknownAndLegacyTargets() {
        assertFalse(RuntimePermissionSafety.canToggle("android.permission.RECORD_AUDIO", requested, 35, 0x01))
        assertFalse(RuntimePermissionSafety.canToggle("android.permission.CAMERA", requested, 35, null))
        assertFalse(RuntimePermissionSafety.canToggle("android.permission.CAMERA", requested, 22, 0x01))
    }
}
