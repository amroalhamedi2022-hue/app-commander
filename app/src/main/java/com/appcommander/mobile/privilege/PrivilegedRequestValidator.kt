package com.appcommander.mobile.privilege

/**
 * Validates every IPC request inside the elevated UserService, independently
 * of the app UI. No command accepts shell syntax, flags or path separators.
 * An invalid request is denied before launching pm/am/appops.
 */
internal object PrivilegedRequestValidator {
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    private val classPattern = Regex("\\.?[A-Za-z_\\$][A-Za-z0-9_\\$]*(\\.[A-Za-z_\\$][A-Za-z0-9_\\$]*)*")

    fun packageNameError(packageName: String): String? =
        if (packageName.length !in 1..255 || !packagePattern.matches(packageName)) {
            "Invalid Android package name"
        } else null

    fun error(operation: Int, packageName: String, argument: String): String? {
        packageNameError(packageName)?.let { return it }
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
        // Android permits manifest components implemented in a different
        // Java namespace (for example, a library activity). Syntax alone
        // cannot prove ownership: the elevated service verifies the class
        // against the target package's declared components before executing.
        return if (classPattern.matches(argument) &&
            (argument.startsWith(".") || argument.contains('.'))) null
            else "Invalid component class name"
    }
}
