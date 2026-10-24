package com.appcommander.mobile.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.appcommander.mobile.model.ApkInspection
import com.appcommander.mobile.model.AppDetails
import com.appcommander.mobile.model.AppEntry
import com.appcommander.mobile.model.CapabilityState
import com.appcommander.mobile.model.ComponentEntry
import com.appcommander.mobile.model.PermissionEntry
import com.appcommander.mobile.privilege.ShizukuController
import java.security.MessageDigest

class AppRepository(private val context: Context) {
    private val pm = context.packageManager

    fun loadApps(): List<AppEntry> {
        val usage = usageByPackage()
        @Suppress("DEPRECATION")
        return pm.getInstalledApplications(0)
            .asSequence()
            .mapNotNull { info ->
                runCatching {
                    @Suppress("DEPRECATION")
                    val pkg = pm.getPackageInfo(info.packageName, 0)
                    val stat = usage[info.packageName]
                    AppEntry(
                        label = info.loadLabel(pm).toString().ifBlank { info.packageName },
                        packageName = info.packageName,
                        versionName = pkg.versionName.orEmpty().ifBlank { "—" },
                        targetSdk = info.targetSdkVersion,
                        isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        enabled = info.enabled,
                        lastUpdateTime = pkg.lastUpdateTime,
                        usageMillis = stat?.totalTimeInForeground ?: 0L,
                        lastTimeUsed = stat?.lastTimeUsed ?: 0L,
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun loadDetails(entry: AppEntry): AppDetails {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.MATCH_DISABLED_COMPONENTS
        @Suppress("DEPRECATION")
        val pkg = pm.getPackageInfo(entry.packageName, flags)
        return AppDetails(
            app = entry,
            permissions = pkg.requestedPermissions.orEmpty().mapIndexed { index, name ->
                val granted = (pkg.requestedPermissionsFlags?.getOrNull(index) ?: 0) and
                    PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                PermissionEntry(name, granted)
            }.sortedBy { it.name },
            activities = pkg.activities.orEmpty().map {
                ComponentEntry(it.name, it.exported, it.enabled)
            }.sortedBy { it.name },
            services = pkg.services.orEmpty().map {
                ComponentEntry(it.name, it.exported, it.enabled)
            }.sortedBy { it.name },
            receivers = pkg.receivers.orEmpty().map {
                ComponentEntry(it.name, it.exported, it.enabled)
            }.sortedBy { it.name },
            apkPath = pkg.applicationInfo?.sourceDir.orEmpty(),
        )
    }

    fun capabilities(): CapabilityState {
        return CapabilityState(
            usageAccess = hasUsageAccess(),
            shizukuInstalled = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("moe.shizuku.privileged.api", 0)
            }.isSuccess,
            shizukuRunning = ShizukuController.isRunning(),
            shizukuGranted = ShizukuController.hasPermission(),
        )
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun openApp(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun launchActivity(packageName: String, className: String): Boolean = runCatching {
        context.startActivity(
            Intent().setComponent(ComponentName(packageName, className))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    fun openAppInfo(packageName: String) {
        startSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName)
    }

    fun openPermissionSettings(packageName: String) {
        startSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageName)
    }
    fun openAutoStartSettings(packageName: String) {
        val xiaomi = Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            )
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(xiaomi) }.onFailure { openAppInfo(packageName) }
    }
    fun openBatterySettings(packageName: String) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openAppInfo(packageName) }
    }

    fun requestUninstall(packageName: String) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openUsageAccess() {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startSettings(action: String, packageName: String) {
        val primary = Intent(action, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(primary) }.onFailure {
            runCatching { context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ) }
        }
    }

    fun inspectApk(uri: Uri): ApkInspection {
        // SAF metadata is optional and untrusted: provider failures or spoofed
        // labels must not prevent inspecting otherwise readable APK bytes.
        val displayName = ApkDisplayName.safe(runCatching { queryDisplayName(uri) }.getOrNull())
        // The URI belongs to an untrusted provider. Use a unique cache file,
        // enforce a streaming size cap, and delete it on EVERY exit path.
        return ApkCacheCopy.withTemporaryApk(context.cacheDir) { cached ->
            val copied = context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "تعذّر فتح الملف" }
                cached.outputStream().use { output ->
                    ApkCacheCopy.copyWithSha256(input, output)
                }
            }
            // Central-directory-only guard: never extract untrusted entries.
            // This does not verify APK signing or certify that the APK is safe.
            ApkArchivePreflight.check(cached)
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_SIGNING_CERTIFICATES
            @Suppress("DEPRECATION")
            val archiveInfo = pm.getPackageArchiveInfo(cached.absolutePath, flags)
            val pkg = requireNotNull(archiveInfo) { "الملف ليس APK صالحًا أو لا يمكن قراءته" }
            pkg.applicationInfo?.sourceDir = cached.absolutePath
            pkg.applicationInfo?.publicSourceDir = cached.absolutePath
            val label = pkg.applicationInfo?.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg.packageName }
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) pkg.applicationInfo?.minSdkVersion ?: 0 else 0
            ApkInspection(
                fileName = displayName,
                label = label,
                packageName = pkg.packageName,
                versionName = pkg.versionName.orEmpty().ifBlank { "—" },
                versionCode = pkg.longVersionCode,
                minSdk = minSdk,
                targetSdk = pkg.applicationInfo?.targetSdkVersion ?: 0,
                permissions = pkg.requestedPermissions?.sorted().orEmpty(),
                activities = pkg.activities?.size ?: 0,
                services = pkg.services?.size ?: 0,
                receivers = pkg.receivers?.size ?: 0,
                sizeBytes = copied.sizeBytes,
                fileSha256 = copied.sha256,
                signerSha256 = runCatching {
                    pkg.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }
                }.getOrDefault(emptyList()),
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return null
    }

    private fun usageByPackage() = if (!hasUsageAccess()) emptyMap() else {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        runCatching {
            manager.queryAndAggregateUsageStats(now - 24 * 60 * 60 * 1000L, now)
        }.getOrDefault(emptyMap())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

}
