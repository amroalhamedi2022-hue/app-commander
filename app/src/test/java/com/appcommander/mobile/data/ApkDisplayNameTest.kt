package com.appcommander.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApkDisplayNameTest {
    @Test fun missingOrUnusableNamesHaveSafeFallback() {
        assertEquals("selected.apk", ApkDisplayName.safe(null))
        assertEquals("selected.apk", ApkDisplayName.safe("   "))
        assertEquals("selected.apk", ApkDisplayName.safe("/\\\u202e"))
    }

    @Test fun stripsProviderPathsAndDirectionSpoofing() {
        assertEquals("example.apk", ApkDisplayName.safe("../../folder\\example.apk"))
        assertEquals("safe.apk", ApkDisplayName.safe("\u202Esa\u202Dfe.apk\u2069"))
        assertEquals("ملف.apk", ApkDisplayName.safe("ملف.apk"))
    }

    @Test fun limitsVeryLongProviderLabelsAndDropsControlCharacters() {
        val safe = ApkDisplayName.safe("a".repeat(10000) + ".apk")
        assertEquals(120, safe.length)
        assertEquals("ab.apk", ApkDisplayName.safe("a\u0000b\n.apk"))
        assertFalse(safe.contains('/'))
    }
}
