package com.appcommander.mobile.privilege

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source contract: all app-side Shizuku mutations must pass the dialog gate. */
class ShizukuUiConsentContractTest {
    @Test fun noDirectPrivilegedMutationBypassesConfirmationDialog() {
        val source = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        assertTrue(source.contains("AlertDialog("))
        assertTrue(source.contains("PrivilegedActionConsent.description("))
        assertTrue(source.contains("pendingPrivileged = PendingPrivilegedAction("))
        assertTrue(source.contains("runCommand(action.onSuccess, action.execute)"))
        assertEquals(
            "Only the confirmed action may call runCommand",
            2, Regex("""\brunCommand\(""").findAll(source).count(), // declaration + confirmation
        )
        for (name in listOf("forceStop", "freeze", "unfreeze", "blockBackground",
            "allowBackground", "setComponent", "setPermission")) {
            assertTrue("Missing Shizuku action $name", source.contains("ShizukuController.$name("))
        }
    }
}
