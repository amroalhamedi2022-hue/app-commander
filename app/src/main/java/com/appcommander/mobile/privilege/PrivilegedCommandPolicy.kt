package com.appcommander.mobile.privilege

/**
 * Last-line guard for the elevated Shizuku UserService, independent of UI checks.
 * No root or Device Owner provisioning is involved. Recovery operations stay
 * available even for protected packages.
 *
 * The dynamic default launcher and input-method checks remain in
 * CriticalPackageGuard on the app side; this list protects known essentials
 * if a caller ever bypasses the app-side path.
 */
internal object PrivilegedCommandPolicy {
    private val essentialPackages = setOf(
        "android",
        "com.appcommander.mobile",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.miui.securitycenter",
        "com.lbe.security.miui",
        "com.miui.powerkeeper",
        "com.miui.home",
        "moe.shizuku.privileged.api",
    )

    private val destructiveOperations = setOf(
        PrivilegedOperation.FORCE_STOP,
        PrivilegedOperation.DISABLE_PACKAGE,
        PrivilegedOperation.SUSPEND_PACKAGE,
        PrivilegedOperation.BLOCK_BACKGROUND,
        PrivilegedOperation.BLOCK_ANY_BACKGROUND,
        PrivilegedOperation.REVOKE_PERMISSION,
        PrivilegedOperation.DISABLE_COMPONENT,
    )

    private val recoveryOperations = setOf(
        PrivilegedOperation.ENABLE_PACKAGE,
        PrivilegedOperation.UNSUSPEND_PACKAGE,
        PrivilegedOperation.ALLOW_BACKGROUND,
        PrivilegedOperation.ALLOW_ANY_BACKGROUND,
        PrivilegedOperation.GRANT_PERMISSION,
        PrivilegedOperation.ENABLE_COMPONENT,
    )

    // This is a final defense-in-depth gate: unknown integer opcodes must not
    // be treated as harmless merely because they are absent from the destructive set.
    private val supportedOperations = destructiveOperations + recoveryOperations

    fun isDestructive(operation: Int): Boolean = operation in destructiveOperations

    fun isProtected(packageName: String, ownPackageName: String = "com.appcommander.mobile"): Boolean =
        packageName == ownPackageName || packageName in essentialPackages

    fun allows(operation: Int, packageName: String, ownPackageName: String = "com.appcommander.mobile"): Boolean =
        operation in supportedOperations &&
            (operation !in destructiveOperations || !isProtected(packageName, ownPackageName))
}
