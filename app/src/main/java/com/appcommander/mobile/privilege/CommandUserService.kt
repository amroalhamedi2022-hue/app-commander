package com.appcommander.mobile.privilege

import android.content.Context
import androidx.annotation.Keep

@Keep
class CommandUserService() : ICommandService.Stub() {
    @Keep
    constructor(context: Context) : this() { appPackageName = context.packageName }

    private var appPackageName = "com.appcommander.mobile"

    override fun destroy() {
        System.exit(0)
    }

    override fun execute(operation: Int, packageName: String, argument: String): String {
        val pkg = safe(packageName)
        val arg = argument.takeIf { it.isNotBlank() }?.let(::safe)
        // Defense in depth: enforce critical-package safety in the privileged
        // process too, not only in the ordinary app-side ShizukuController.
        if (!PrivilegedCommandPolicy.allows(operation, pkg, appPackageName)) {
            return "exit=1\nProtected critical Android package: destructive operation denied"
        }
        val command = when (operation) {
            PrivilegedOperation.FORCE_STOP -> arrayOf("am", "force-stop", "--user", "0", pkg)
            PrivilegedOperation.DISABLE_PACKAGE -> arrayOf("pm", "disable-user", "--user", "0", pkg)
            PrivilegedOperation.ENABLE_PACKAGE -> arrayOf("pm", "enable", "--user", "0", pkg)
            PrivilegedOperation.SUSPEND_PACKAGE -> arrayOf("pm", "suspend", "--user", "0", pkg)
            PrivilegedOperation.UNSUSPEND_PACKAGE -> arrayOf("pm", "unsuspend", "--user", "0", pkg)
            PrivilegedOperation.BLOCK_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_IN_BACKGROUND", "ignore")
            PrivilegedOperation.BLOCK_ANY_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
            PrivilegedOperation.ALLOW_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_IN_BACKGROUND", "allow")
            PrivilegedOperation.ALLOW_ANY_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_ANY_IN_BACKGROUND", "allow")
            PrivilegedOperation.GRANT_PERMISSION -> arrayOf("pm", "grant", "--user", "0", pkg, requireNotNull(arg))
            PrivilegedOperation.REVOKE_PERMISSION -> arrayOf("pm", "revoke", "--user", "0", pkg, requireNotNull(arg))
            PrivilegedOperation.ENABLE_COMPONENT -> arrayOf("pm", "enable", "--user", "0", "$pkg/${requireNotNull(arg)}")
            PrivilegedOperation.DISABLE_COMPONENT -> arrayOf("pm", "disable-user", "--user", "0", "$pkg/${requireNotNull(arg)}")
            else -> error("Unsupported privileged operation")
        }
        // Typed argv only: bounded time and bounded stdout/stderr prevent
        // hung package-manager commands from pinning a privileged Binder call.
        return PrivilegedProcessRunner.collect(Runtime.getRuntime().exec(command))
    }

    private fun safe(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.$]+"))) { "Unsafe command argument" }
        return value
    }
}
