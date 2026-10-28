package com.appcommander.mobile.privilege

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.Keep

@Keep
class CommandUserService() : ICommandService.Stub() {
    @Keep
    constructor(context: Context) : this() {
        appPackageName = context.packageName
        serviceContext = context.applicationContext
    }

    private var appPackageName = "com.appcommander.mobile"
    private var serviceContext: Context? = null

    override fun destroy() {
        System.exit(0)
    }

    override fun execute(operation: Int, packageName: String, argument: String): String {
        val invalid = PrivilegedRequestValidator.error(operation, packageName, argument)
        if (invalid != null) return "exit=1\n" + invalid
        val pkg = packageName
        val arg = argument.takeIf { it.isNotEmpty() }
        // Defense in depth: enforce critical-package safety in the privileged
        // process too, not only in the ordinary app-side ShizukuController.
        val dynamicReason = if (PrivilegedCommandPolicy.isDestructive(operation)) {
            // A user-installed default launcher or keyboard may not be in the
            // static critical-package list. Re-check from the elevated process.
            // Fail closed if the elevated process cannot inspect the active
            // launcher and input method. Static allowlists alone are not enough.
            serviceContext?.let { CriticalPackageGuard.reason(it, pkg) }
                ?: if (serviceContext == null) "Cannot verify critical packages" else null
        } else null
        if (!PrivilegedCommandPolicy.allows(operation, pkg, appPackageName) || dynamicReason != null) {
            return "exit=1\n" + (dynamicReason ?: "Protected critical Android package: destructive operation denied")
        }
        // pm accepts a package/class pair even if the class name lives in
        // a library namespace. Validate against the actual manifest inventory
        // instead of guessing ownership from the Java namespace. Never run a
        // privileged component command if PackageManager cannot verify it.
        if (operation == PrivilegedOperation.ENABLE_COMPONENT ||
            operation == PrivilegedOperation.DISABLE_COMPONENT) {
            val declared = serviceContext?.let { context ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val info = context.packageManager.getPackageInfo(
                        pkg, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                            PackageManager.MATCH_DISABLED_COMPONENTS,
                    )
                    val names = buildSet {
                        info.activities?.forEach { add(it.name) }
                        info.services?.forEach { add(it.name) }
                        info.receivers?.forEach { add(it.name) }
                        info.providers?.forEach { add(it.name) }
                    }
                    DeclaredComponentSafety.isDeclared(pkg, requireNotNull(arg), names)
                }.getOrDefault(false)
            } ?: false
            if (!declared) return "exit=1\nComponent not declared by target package"
        }
        // Never forward a signature/normal/special permission to pm grant or
        // revoke. Confirm the target actually requests a dangerous runtime
        // permission in the elevated process; fail closed on visibility errors.
        if (operation == PrivilegedOperation.GRANT_PERMISSION ||
            operation == PrivilegedOperation.REVOKE_PERMISSION) {
            val eligible = serviceContext?.let { context ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                    @Suppress("DEPRECATION")
                    val level = context.packageManager.getPermissionInfo(requireNotNull(arg), 0).protectionLevel
                    RuntimePermissionSafety.canToggle(
                        requireNotNull(arg), info.requestedPermissions.orEmpty().toList(),
                        info.applicationInfo?.targetSdkVersion ?: 0, level,
                    )
                }.getOrDefault(false)
            } ?: false
            if (!eligible) return "exit=1\nPermission is not a requested runtime permission"
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
        return PrivilegedProcessRunner.runAllowed { Runtime.getRuntime().exec(command) }
    }

}
