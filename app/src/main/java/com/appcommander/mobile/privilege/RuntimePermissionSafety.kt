package com.appcommander.mobile.privilege

/**
 * pm grant/revoke can change requested runtime permissions, not signature,
 * privileged, normal or special-access permissions. Android is the final
 * authority; this gate prevents misleading controls and unsafe IPC attempts.
 * The constants match PackageManager.PROTECTION_MASK_BASE / DANGEROUS.
 */
internal object RuntimePermissionSafety {
    private const val BASE_MASK = 0x0f
    private const val DANGEROUS = 0x01

    fun canToggle(
        permission: String,
        requested: Collection<String>,
        targetSdk: Int,
        protectionLevel: Int?,
    ): Boolean = targetSdk >= 23 &&
        permission in requested &&
        protectionLevel != null &&
        (protectionLevel and BASE_MASK) == DANGEROUS
}
