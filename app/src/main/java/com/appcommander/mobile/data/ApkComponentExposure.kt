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

    /**
     * Content providers have independent read and write permission gates.
     * A permission protecting only one direction must not hide the other.
     * URI grants and path-specific permissions are counted separately or
     * noted as limitations; none of these counts proves public reachability.
     */
    data class ProviderCounts(
        val exported: Int,
        val withoutReadPermission: Int,
        val withoutWritePermission: Int,
        val uriGrantCapable: Int,
    )

    fun <T> countProviders(
        providers: Array<T>?,
        exported: (T) -> Boolean,
        readPermission: (T) -> String?,
        writePermission: (T) -> String?,
        grantsUriPermissions: (T) -> Boolean,
    ): ProviderCounts {
        if (providers == null) return ProviderCounts(0, 0, 0, 0)
        var exportedCount = 0
        var noReadGate = 0
        var noWriteGate = 0
        var uriGrantCount = 0
        for (provider in providers) {
            if (!exported(provider)) continue
            exportedCount++
            if (readPermission(provider).isNullOrBlank()) noReadGate++
            if (writePermission(provider).isNullOrBlank()) noWriteGate++
            if (grantsUriPermissions(provider)) uriGrantCount++
        }
        return ProviderCounts(exportedCount, noReadGate, noWriteGate, uriGrantCount)
    }
}
