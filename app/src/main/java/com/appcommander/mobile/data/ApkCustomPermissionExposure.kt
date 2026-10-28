package com.appcommander.mobile.data

/**
 * Counts exported gates protected by a permission defined in the inspected APK
 * whose base protection level is normal or dangerous. It deliberately does not
 * infer that a caller can reach a component or that the APK is vulnerable.
 */
internal object ApkCustomPermissionExposure {
    data class ProviderCounts(
        val directionsUsingNonSignatureAppPermission: Int,
        val pathRulesUsingNonSignatureAppPermission: Int,
    )

    data class PathPermissionGate(
        val readPermission: String?,
        val writePermission: String?,
    )

    fun <T> countComponents(
        components: Array<T>?,
        exported: (T) -> Boolean,
        requiredPermission: (T) -> String?,
        nonSignatureAppPermissions: Set<String>,
    ): Int {
        if (components == null || nonSignatureAppPermissions.isEmpty()) return 0
        return components.count { component ->
            exported(component) && requiredPermission(component)?.let(nonSignatureAppPermissions::contains) == true
        }
    }

    fun <T> countProviders(
        providers: Array<T>?,
        exported: (T) -> Boolean,
        readPermission: (T) -> String?,
        writePermission: (T) -> String?,
        pathPermissions: (T) -> List<PathPermissionGate>,
        nonSignatureAppPermissions: Set<String>,
    ): ProviderCounts {
        if (providers == null || nonSignatureAppPermissions.isEmpty()) return ProviderCounts(0, 0)
        var directions = 0
        var pathRules = 0
        for (provider in providers) {
            if (!exported(provider)) continue
            val defaultRead = readPermission(provider).takeIf(PermissionNameSafety::isWellFormed)
            val defaultWrite = writePermission(provider).takeIf(PermissionNameSafety::isWellFormed)
            if (defaultRead != null && defaultRead in nonSignatureAppPermissions) directions++
            if (defaultWrite != null && defaultWrite in nonSignatureAppPermissions) directions++

            for (rule in pathPermissions(provider)) {
                // A missing/malformed path-specific gate inherits the provider's
                // default direction, matching the existing exposure inventory.
                val effectiveRead = rule.readPermission.takeIf(PermissionNameSafety::isWellFormed) ?: defaultRead
                val effectiveWrite = rule.writePermission.takeIf(PermissionNameSafety::isWellFormed) ?: defaultWrite
                if (effectiveRead != null && effectiveRead in nonSignatureAppPermissions) pathRules++
                if (effectiveWrite != null && effectiveWrite in nonSignatureAppPermissions) pathRules++
            }
        }
        return ProviderCounts(directions, pathRules)
    }
}
