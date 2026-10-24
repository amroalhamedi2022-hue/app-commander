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
)

data class PermissionEntry(
    val name: String,
    val granted: Boolean,
)

data class AppDetails(
    val app: AppEntry,
    val permissions: List<PermissionEntry>,
    val activities: List<ComponentEntry>,
    val services: List<ComponentEntry>,
    val receivers: List<ComponentEntry>,
    val apkPath: String,
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
    val sizeBytes: Long,
    val fileSha256: String,
    val signerSha256: List<String>,
)

data class CapabilityState(
    val usageAccess: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuGranted: Boolean,
)
