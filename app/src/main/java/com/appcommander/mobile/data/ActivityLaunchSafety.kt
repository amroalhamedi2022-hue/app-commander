package com.appcommander.mobile.data

import com.appcommander.mobile.model.ComponentEntry

/**
 * Conservative Activity Launcher policy for another app's public activities.
 * No Shizuku, reflection, hidden APIs, shell, or bypass of Android export rules.
 * The UI inventory is only advisory; AppRepository re-queries PackageManager
 * immediately before dispatch to handle stale exported/enabled state.
 */
internal object ActivityLaunchSafety {
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val permissionPattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val classPattern = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")

    fun validNames(packageName: String, className: String): Boolean =
        packageName.length in 3..255 && className.length in 3..512 &&
            packagePattern.matches(packageName) && classPattern.matches(className)

    /**
     * The caller must hold any permission enforced by the target activity.
     * A failed Android permission query is NOT evidence of access.
     */
    fun callerHasPermission(permission: String?, check: (String) -> Boolean): Boolean {
        if (permission == null || permission.isEmpty()) return true
        // A malformed non-empty permission must never be treated as unrestricted.
        if (permission.length > 255 || !permissionPattern.matches(permission)) return false
        return try { check(permission) } catch (_: Exception) { false }
    }

    fun eligible(
        packageName: String,
        className: String,
        exported: Boolean,
        componentEnabled: Boolean,
        appEnabled: Boolean,
        callerHasPermission: Boolean = true,
    ): Boolean = validNames(packageName, className) &&
        exported && componentEnabled && appEnabled && callerHasPermission

    fun visible(
        activities: List<ComponentEntry>,
        query: String,
        exportedOnly: Boolean,
    ): List<ComponentEntry> {
        val needle = query.trim()
        return activities.filter { activity ->
            (!exportedOnly || activity.exported) &&
                (needle.isEmpty() || activity.name.contains(needle, ignoreCase = true))
        }
    }
}
