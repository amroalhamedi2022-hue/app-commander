package com.appcommander.mobile.privilege

import org.junit.Assert.*
import org.junit.Test

class PrivilegedActionConsentTest {
    @Test fun everySupportedShizukuOperationRequiresFreshExplicitConsent() {
        for (operation in 1..13) {
            val argument = when (operation) {
                PrivilegedOperation.GRANT_PERMISSION, PrivilegedOperation.REVOKE_PERMISSION ->
                    "android.permission.CAMERA"
                PrivilegedOperation.ENABLE_COMPONENT, PrivilegedOperation.DISABLE_COMPONENT ->
                    "com.example.safe.SettingsActivity"
                else -> ""
            }
            val text = PrivilegedActionConsent.description(operation, "com.example.safe", argument)
            assertNotNull("operation=$operation", text)
            assertTrue(text!!.contains("com.example.safe"))
            assertTrue(text.contains("موافقتك"))
            if (argument.isNotEmpty()) assertTrue(text.contains(argument))
        }
    }

    @Test fun rejectsUnknownOperationInvalidPackageAndUnexpectedArguments() {
        assertNull(PrivilegedActionConsent.description(99, "com.example.safe"))
        assertNull(PrivilegedActionConsent.description(PrivilegedOperation.FORCE_STOP, "com.example.safe;rm"))
        assertNull(PrivilegedActionConsent.description(PrivilegedOperation.FORCE_STOP, "com.example.safe", "unexpected"))
        assertNull(PrivilegedActionConsent.description(PrivilegedOperation.REVOKE_PERMISSION, "com.example.safe", "CAMERA"))
        assertNull(PrivilegedActionConsent.description(PrivilegedOperation.DISABLE_COMPONENT, "com.example.safe", "../Main"))
    }

    @Test fun destructiveAndRecoveryDescriptionsStayDistinct() {
        val destructive = PrivilegedActionConsent.description(PrivilegedOperation.DISABLE_PACKAGE, "com.example.safe")!!
        val recovery = PrivilegedActionConsent.description(PrivilegedOperation.ENABLE_PACKAGE, "com.example.safe")!!
        assertTrue(destructive.contains("يفقد وظائف"))
        assertFalse(recovery.contains("يفقد وظائف"))
    }
}
