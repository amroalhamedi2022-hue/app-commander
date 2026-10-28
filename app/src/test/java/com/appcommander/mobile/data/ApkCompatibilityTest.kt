package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ApkCompatibilityTest {
    @Test fun minimumAboveDeviceIsIncompatible() {
        val result = ApkCompatibility.assess(35, 33)
        assertEquals(ApkCompatibility.Status.BELOW_MINIMUM, result.status)
        assertTrue(result.description.contains("API 35"))
    }

    @Test fun equalOrOlderMinimumDoesNotPromiseInstallSuccess() {
        for (min in listOf(21, 33)) {
            val result = ApkCompatibility.assess(min, 33)
            assertEquals(ApkCompatibility.Status.MEETS_MINIMUM, result.status)
            assertTrue(result.description.contains("غير مضمون"))
        }
    }

    @Test fun unknownSdkNeverMakesCompatibilityClaim() {
        assertEquals(ApkCompatibility.Status.UNKNOWN, ApkCompatibility.assess(0, 33).status)
        assertEquals(ApkCompatibility.Status.UNKNOWN, ApkCompatibility.assess(26, 0).status)
        assertEquals(ApkCompatibility.Status.UNKNOWN, ApkCompatibility.assess(-1, 33).status)
    }

    @Test fun androidUiShowsDeviceSdkAgainstInspectedMinimum() {
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        assertTrue(ui.contains("ApkCompatibility.assess("))
        assertTrue(ui.contains("apk.minSdk, android.os.Build.VERSION.SDK_INT"))
        assertTrue(ui.contains("توافق هذا الجهاز"))
    }
}
