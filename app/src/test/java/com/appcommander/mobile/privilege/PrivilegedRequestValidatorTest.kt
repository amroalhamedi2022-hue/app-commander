package com.appcommander.mobile.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrivilegedRequestValidatorTest {
    @Test fun validOperationsAndArgumentsAreAccepted() {
        assertNull(PrivilegedRequestValidator.error(PrivilegedOperation.FORCE_STOP, "org.example.reader", ""))
        assertNull(PrivilegedRequestValidator.error(PrivilegedOperation.ENABLE_PACKAGE, "android", ""))
        assertNull(PrivilegedRequestValidator.error(
            PrivilegedOperation.GRANT_PERMISSION, "org.example.reader", "android.permission.CAMERA"))
        assertNull(PrivilegedRequestValidator.error(
            PrivilegedOperation.DISABLE_COMPONENT, "org.example.reader", ".MainActivity"))
        assertNull(PrivilegedRequestValidator.error(
            PrivilegedOperation.ENABLE_COMPONENT, "org.example.reader", "org.example.reader.MainActivity"))
    }

    @Test fun rejectsUnrecognizedOperationAndUnexpectedArguments() {
        assertEquals("Unsupported privileged operation",
            PrivilegedRequestValidator.error(999, "org.example.reader", ""))
        assertNotNull(PrivilegedRequestValidator.error(
            PrivilegedOperation.FORCE_STOP, "org.example.reader", "unused"))
        assertNotNull(PrivilegedRequestValidator.error(
            PrivilegedOperation.GRANT_PERMISSION, "org.example.reader", ""))
    }

    @Test fun rejectsShellLikeAndMalformedPackageNames() {
        for (pkg in listOf("", "-user", "com..example", ".example", "com.example/", "com.example;id",
                "com.example\nother", "1bad.name", "x".repeat(256))) {
            assertNotNull(pkg, PrivilegedRequestValidator.error(PrivilegedOperation.FORCE_STOP, pkg, ""))
        }
    }

    @Test fun rejectsUnsafePermissionAndComponentArguments() {
        for (arg in listOf("", "-flag", "android.permission.CAMERA;id", "../Main",
                "Main/Other", "a\nother", "x".repeat(256))) {
            assertNotNull(arg, PrivilegedRequestValidator.error(
                PrivilegedOperation.DISABLE_COMPONENT, "org.example.reader", arg))
        }
        for (arg in listOf("CAMERA", "android..permission.CAMERA", "--user", "a/b")) {
            assertNotNull(arg, PrivilegedRequestValidator.error(
                PrivilegedOperation.REVOKE_PERMISSION, "org.example.reader", arg))
        }
    }
    @Test fun componentSyntaxAllowsManifestDeclaredLibraryNamespaces() {
        val pkg = "org.example.reader"
        for (allowed in listOf(".MainActivity", ".ui.SettingsActivity",
                "org.example.reader.MainActivity", "com.vendor.library.SettingsActivity")) {
            assertNull(allowed, PrivilegedRequestValidator.error(
                PrivilegedOperation.ENABLE_COMPONENT, pkg, allowed))
        }
        for (invalid in listOf("MainActivity", "org..bad.Activity", "-Activity")) {
            assertNotNull(invalid, PrivilegedRequestValidator.error(
                PrivilegedOperation.DISABLE_COMPONENT, pkg, invalid))
        }
    }

}
