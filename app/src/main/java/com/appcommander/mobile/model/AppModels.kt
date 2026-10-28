package com.appcommander.mobile.model

data class AppEntry(
    val label: String,
    val packageName: String,
    val versionName: String,
    val targetSdk: Int,
    val isSystem: Boolean,
    val enabled: Boolean,
    val lastUpdateTime: Long,
    val usageMillis: Long = 0L,
    val lastTimeUsed: Long = 0L,
)

data class ComponentEntry(
    val name: String,
    val exported: Boolean,
    val enabled: Boolean,
    // Activity launch is not necessarily allowed merely because it is exported.
    // Android may require a permission held by the calling application.
    val requiredPermission: String? = null,
    val callerHasPermission: Boolean = true,
)

data class PermissionEntry(
    val name: String,
    val granted: Boolean,
    val runtimeMutable: Boolean = false,
)

data class AppDetails(
    val app: AppEntry,
    val permissions: List<PermissionEntry>,
    val activities: List<ComponentEntry>,
    val services: List<ComponentEntry>,
    val receivers: List<ComponentEntry>,
    val apkPath: String,
)

enum class ApkCertificateParseStatus { PARSED, EMPTY_INPUT, OVERSIZED, INVALID_DER }

data class ApkCertificateMetadata(
    val sha256: String,
    val subject: String?,
    val issuer: String?,
    val serialNumberHex: String?,
    val notBeforeEpochMillis: Long?,
    val notAfterEpochMillis: Long?,
    val signatureAlgorithm: String?,
    val publicKeyAlgorithm: String?,
    val parseStatus: ApkCertificateParseStatus,
)

data class ApkInspection(
    val fileName: String,
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val activities: Int,
    val services: Int,
    val receivers: Int,
    val providers: Int,
    val sizeBytes: Long,
    val fileSha256: String,
    val signerSha256: List<String>,
    // Null means the platform did not provide this signing metadata; it is not proof of unsigned content.
    val hasMultipleSigners: Boolean? = null,
    val hasPastSigningCertificates: Boolean? = null,
    // Ordered oldest-to-current only for a single signer with a reported rotation history.
    val signingHistorySha256: List<String> = emptyList(),
    // Counts of exported manifest components; not proof that a caller can invoke them.
    val exportedActivities: Int = 0,
    val exportedServices: Int = 0,
    val exportedReceivers: Int = 0,
    val unguardedExportedActivities: Int = 0,
    val unguardedExportedServices: Int = 0,
    val unguardedExportedReceivers: Int = 0,
    val exportedProviders: Int = 0,
    val providersWithoutReadPermission: Int = 0,
    val providersWithoutWritePermission: Int = 0,
    val providersGrantUriPermissions: Int = 0,
    val providerPathPermissionRules: Int = 0,
    val providerPathRulesWithoutReadPermission: Int = 0,
    val providerPathRulesWithoutWritePermission: Int = 0,
    // Gates backed by permissions declared by this APK at normal/dangerous base level.
    // These counts are review prompts, not proof that another app can reach the component.
    val exportedComponentsUsingNonSignatureAppPermission: Int = 0,
    val providerDirectionsUsingNonSignatureAppPermission: Int = 0,
    val providerPathRulesUsingNonSignatureAppPermission: Int = 0,
    // X.509 certificate fields are descriptive metadata; they do not verify the APK signature.
    val signerCertificateDetails: List<ApkCertificateMetadata> = emptyList(),
    val signingHistoryDetails: List<ApkCertificateMetadata> = emptyList(),
)

data class CapabilityState(
    val usageAccess: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
)
