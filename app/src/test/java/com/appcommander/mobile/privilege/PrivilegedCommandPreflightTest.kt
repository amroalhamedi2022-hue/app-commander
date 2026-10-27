package com.appcommander.mobile.privilege

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrivilegedCommandPreflightTest {
    @Test fun malformedRequestsReturnBeforeDispatchingAnyPrivilegedWork() = runBlocking {
        var dispatchCount = 0
        val cases = listOf(
            Triple(PrivilegedOperation.FORCE_STOP, "com..example", ""),
            Triple(PrivilegedOperation.FORCE_STOP, "org.example;id", ""),
            Triple(999, "org.example.reader", ""),
            Triple(PrivilegedOperation.GRANT_PERMISSION, "org.example.reader", "android.permission.CAMERA;id"),
            Triple(PrivilegedOperation.DISABLE_COMPONENT, "org.example.reader", "../MainActivity"),
        )

        for ((operation, packageName, argument) in cases) {
            val result = PrivilegedCommandPreflight.execute(operation, packageName, argument) {
                dispatchCount++
                CommandResult(true, "should not execute")
            }
            assertFalse("invalid request must be denied", result.success)
            assertTrue("a useful validation error must be returned", result.output.isNotBlank())
        }
        assertEquals("invalid requests must never reach the Binder dispatch callback", 0, dispatchCount)
    }

    @Test fun validRequestDispatchesExactlyOnceAndPreservesServiceResult() = runBlocking {
        var dispatchCount = 0
        val expected = CommandResult(false, "service reported a safe failure")
        val actual = PrivilegedCommandPreflight.execute(
            PrivilegedOperation.FORCE_STOP, "org.example.reader", "",
        ) {
            dispatchCount++
            expected
        }

        assertSame(expected, actual)
        assertEquals(1, dispatchCount)
    }

    @Test fun controllerInvokesPreflightBeforePermissionOrBinderLookup() {
        val source = File("src/main/java/com/appcommander/mobile/privilege/ShizukuController.kt").readText()
        val execStart = source.indexOf("private suspend fun exec")
        val preflight = source.indexOf("PrivilegedCommandPreflight.execute", execStart)
        val permission = source.indexOf("if (!hasPermission())", execStart)
        val bind = source.indexOf("val service = obtainService()", execStart)
        assertTrue(execStart >= 0 && preflight > execStart)
        assertTrue("validation must precede permission checks", preflight < permission)
        assertTrue("validation and permission checks must precede service binding", permission < bind)

        val destructiveStart = source.indexOf("private suspend fun destructive")
        val packageValidation = source.indexOf("PrivilegedRequestValidator.packageNameError", destructiveStart)
        val contextLookup = source.indexOf("val context = appContext", destructiveStart)
        val criticalCheck = source.indexOf("CriticalPackageGuard.reason", destructiveStart)
        assertTrue(destructiveStart >= 0 && packageValidation > destructiveStart)
        assertTrue(packageValidation < contextLookup && contextLookup < criticalCheck)
    }
}
