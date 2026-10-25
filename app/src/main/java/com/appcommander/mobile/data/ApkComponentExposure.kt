package com.appcommander.mobile.data

/**
 * Counts externally exported manifest components without claiming that an
 * exported component is reachable or vulnerable. Android intent filters,
 * caller identity, runtime checks and OS policy may restrict invocation.
 *
 * Only a component-level permission is considered here; dynamic access
 * checks performed by the component are not observable from archive metadata.
 */
internal object ApkComponentExposure {
    data class Counts(val exported: Int, val withoutDeclaredPermission: Int)

    fun <T> count(
        components: Array<T>?,
        exported: (T) -> Boolean,
        declaredPermission: (T) -> String?,
    ): Counts {
        if (components == null) return Counts(0, 0)
        var exportedCount = 0
        var withoutPermission = 0
        for (component in components) {
            if (!exported(component)) continue
            exportedCount++
            val permission = declaredPermission(component)
            // Empty or malformed declarations are not reliable protection.
            if (permission.isNullOrBlank() || permission != permission.trim()) {
                withoutPermission++
            }
        }
        return Counts(exportedCount, withoutPermission)
    }
}
