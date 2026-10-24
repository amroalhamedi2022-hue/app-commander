package com.appcommander.mobile.privilege

/**
 * Validates every IPC request inside the elevated UserService, independently
 * of the app UI. No command accepts shell syntax, flags or path separators.
 * An invalid request is denied before launching pm/am/appops.
 */
internal object PrivilegedRequestValidator {
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    private val classPattern = Regex("\\.?[A-Za-z_\\$][A-Za-z0-9_\\$]*(\\.[A-Za-z_\\$][A-Za-z0-9_\\$]*)*")

    fun error(operation: Int, packageName: String, argument: String): String? {
        if (packageName.length !in 1..255 || !packagePattern.matches(packageName)) {
            return "Invalid Android package name"
        }
        val requiresArgument = when (operation) {
            PrivilegedOperation.GRANT_PERMISSION, PrivilegedOperation.REVOKE_PERMISSION,
            PrivilegedOperation.ENABLE_COMPONENT, PrivilegedOperation.DISABLE_COMPONENT -> true
            PrivilegedOperation.FORCE_STOP, PrivilegedOperation.DISABLE_PACKAGE,
            PrivilegedOperation.ENABLE_PACKAGE, PrivilegedOperation.SUSPEND_PACKAGE,
            PrivilegedOperation.UNSUSPEND_PACKAGE, PrivilegedOperation.BLOCK_BACKGROUND,
            PrivilegedOperation.BLOCK_ANY_BACKGROUND, PrivilegedOperation.ALLOW_BACKGROUND,
            PrivilegedOperation.ALLOW_ANY_BACKGROUND -> false
            else -> return "Unsupported privileged operation"
        }
        if (!requiresArgument) {
            return if (argument.isEmpty()) null else "Unexpected command argument"
        }
        if (argument.length !in 1..255) return "Missing or oversized command argument"
        if (operation == PrivilegedOperation.GRANT_PERMISSION ||
            operation == PrivilegedOperation.REVOKE_PERMISSION) {
            return if (argument.contains('.') && packagePattern.matches(argument)) null
                else "Invalid permission name"
        }
        // ComponentName must belong to the requested package. Reject a
        // syntactically valid fully qualified class from another package,
        // rather than passing a misleading cross-package target to pm.
        if (!classPattern.matches(argument) ||
            !(argument.startsWith(".") || argument.startsWith("$packageName."))) {
            return "Component class must belong to target package"
        }
        return null
    }
}
