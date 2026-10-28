package com.appcommander.mobile.privilege

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CriticalTargetSafetyTest {
    @Test fun unknownHomeOrKeyboardFailsClosed() {
        assertNotNull(CriticalTargetSafety.reason("org.example.reader", null, "ime.keyboard"))
        assertNotNull(CriticalTargetSafety.reason("org.example.reader", emptySet(), "ime.keyboard"))
        assertNotNull(CriticalTargetSafety.reason("org.example.reader", setOf("home.launcher"), null))
        assertNotNull(CriticalTargetSafety.reason("org.example.reader", setOf("home.launcher"), ""))
    }

    @Test fun allHomeCandidatesAndCurrentKeyboardAreProtected() {
        val homes = setOf("home.primary", "home.backup")
        assertNotNull(CriticalTargetSafety.reason("home.primary", homes, "ime.keyboard"))
        assertNotNull(CriticalTargetSafety.reason("home.backup", homes, "ime.keyboard"))
        assertNotNull(CriticalTargetSafety.reason("ime.keyboard", homes, "ime.keyboard"))
        assertNull(CriticalTargetSafety.reason("org.example.reader", homes, "ime.keyboard"))
    }
}
