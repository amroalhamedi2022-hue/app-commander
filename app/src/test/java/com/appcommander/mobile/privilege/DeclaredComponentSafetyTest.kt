package com.appcommander.mobile.privilege

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclaredComponentSafetyTest {
    @Test fun acceptsRelativeAndFullyQualifiedManifestComponents() {
        val declared = setOf("org.example.reader.MainActivity", "org.example.reader.ui.Settings")
        assertTrue(DeclaredComponentSafety.isDeclared(
            "org.example.reader", ".MainActivity", declared))
        assertTrue(DeclaredComponentSafety.isDeclared(
            "org.example.reader", "org.example.reader.ui.Settings", declared))
    }

    @Test fun acceptsLibraryNamespaceOnlyWhenActuallyDeclaredByTargetApk() {
        val declared = setOf("com.vendor.library.ReaderActivity")
        assertTrue(DeclaredComponentSafety.isDeclared(
            "org.example.reader", "com.vendor.library.ReaderActivity", declared))
        assertFalse(DeclaredComponentSafety.isDeclared(
            "org.example.reader", "com.vendor.library.HiddenActivity", declared))
    }

    @Test fun failsClosedWhenManifestInventoryIsEmptyOrDifferent() {
        assertFalse(DeclaredComponentSafety.isDeclared(
            "org.example.reader", ".MainActivity", emptySet()))
        assertFalse(DeclaredComponentSafety.isDeclared(
            "org.example.reader", ".MainActivity", setOf("org.other.reader.MainActivity")))
    }
}
