package com.appcommander.mobile.data

/**
 * Conservative syntax gate for manifest-declared Android permission names.
 * This validates the identifier shape only; it does not prove the permission
 * exists, is granted, or provides meaningful runtime protection.
 */
internal object PermissionNameSafety {
    private val pattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    fun isWellFormed(permission: String?): Boolean =
        permission != null && permission.length in 3..255 && pattern.matches(permission)
}
