package com.appcommander.mobile.data

/**
 * Resolves Android PackageManager enabled-state overrides for the installed
 * app and each manifest component. ComponentInfo.enabled by itself is not
 * authoritative after a user/Shizuku override.
 *
 * Unknown or unavailable PackageManager state fails closed: never present a
 * possibly disabled component as safe to launch or toggle.
 *
 * Constants mirror public PackageManager.COMPONENT_ENABLED_STATE_* values.
 * Kept platform-independent for deterministic JVM regression tests.
 */
internal object ComponentEnabledState {
    const val DEFAULT = 0
    const val ENABLED = 1
    const val DISABLED = 2
    const val DISABLED_USER = 3
    const val DISABLED_UNTIL_USED = 4

    fun effective(manifestEnabled: Boolean, setting: Int?): Boolean = when (setting) {
        DEFAULT -> manifestEnabled
        ENABLED -> true
        DISABLED, DISABLED_USER, DISABLED_UNTIL_USED -> false
        else -> false
    }

    fun available(
        manifestComponentEnabled: Boolean,
        componentSetting: Int?,
        manifestAppEnabled: Boolean,
        appSetting: Int?,
    ): Boolean = effective(manifestComponentEnabled, componentSetting) &&
        effective(manifestAppEnabled, appSetting)

    /**
     * A disabled parent app cannot be repaired by enabling one component.
     * For protected targets, allow only recovery of an explicitly disabled
     * component; never offer the destructive disable action.
     */
    fun mayToggleComponent(componentEnabled: Boolean, parentAppEnabled: Boolean, protected: Boolean): Boolean =
        parentAppEnabled && (!protected || !componentEnabled)
}
