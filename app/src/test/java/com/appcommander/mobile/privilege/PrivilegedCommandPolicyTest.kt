package com.appcommander.mobile.privilege

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedCommandPolicyTest {
    private val dangerous = listOf(
        PrivilegedOperation.FORCE_STOP,
        PrivilegedOperation.DISABLE_PACKAGE,
        PrivilegedOperation.SUSPEND_PACKAGE,
        PrivilegedOperation.BLOCK_BACKGROUND,
        PrivilegedOperation.BLOCK_ANY_BACKGROUND,
        PrivilegedOperation.REVOKE_PERMISSION,
        PrivilegedOperation.DISABLE_COMPONENT,
    )
    private val recovery = listOf(
        PrivilegedOperation.ENABLE_PACKAGE,
        PrivilegedOperation.UNSUSPEND_PACKAGE,
        PrivilegedOperation.ALLOW_BACKGROUND,
        PrivilegedOperation.ALLOW_ANY_BACKGROUND,
        PrivilegedOperation.GRANT_PERMISSION,
        PrivilegedOperation.ENABLE_COMPONENT,
    )

    @Test fun privilegedProcessRejectsDangerousCommandsAgainstCriticalPackages() {
        for (pkg in listOf(
            "android", "com.android.systemui", "com.android.settings",
            "com.android.permissioncontroller", "com.miui.home",
            "moe.shizuku.privileged.api", "com.appcommander.mobile"
        )) for (op in dangerous) {
            assertFalse("$pkg / $op", PrivilegedCommandPolicy.allows(op, pkg))
        }
    }

    @Test fun recoveryIsStillAvailableForProtectedPackages() {
        for (op in recovery) {
            assertTrue(PrivilegedCommandPolicy.allows(op, "com.android.systemui"))
            assertTrue(PrivilegedCommandPolicy.allows(op, "com.appcommander.mobile"))
        }
    }

    @Test fun ordinaryPackagesKeepExistingControls() {
        for (op in dangerous + recovery) {
            assertTrue(PrivilegedCommandPolicy.allows(op, "org.example.reader"))
        }
    }

    @Test fun destructiveClassificationMatchesRecoveryExceptions() {
        for (op in dangerous) assertTrue(PrivilegedCommandPolicy.isDestructive(op))
        for (op in recovery) assertFalse(PrivilegedCommandPolicy.isDestructive(op))
    }

    @Test fun runtimeApplicationIdIsProtectedEvenWhenDifferentFromDefault() {
        assertFalse(PrivilegedCommandPolicy.allows(
            PrivilegedOperation.DISABLE_PACKAGE, "com.example.flavor", "com.example.flavor"))
        assertTrue(PrivilegedCommandPolicy.allows(
            PrivilegedOperation.ENABLE_PACKAGE, "com.example.flavor", "com.example.flavor"))
    }
}
