package com.appcommander.mobile.privilege

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Product contract: no Device Admin or Device Owner enrollment surface. */
class NoDeviceAdminContractTest {
    @Test fun manifestDoesNotRequestDeviceAdministration() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("BIND_DEVICE_ADMIN"))
        assertFalse(manifest.contains("DEVICE_ADMIN_ENABLED"))
        assertFalse(manifest.contains("android.app.device_admin"))
        assertFalse(manifest.contains("AppCommanderDeviceAdminReceiver"))
    }
}
