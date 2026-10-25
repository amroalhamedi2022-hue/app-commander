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
import android.provider.DocumentsContract
import com.appcommander.mobile.model.ApkInspection
import com.appcommander.mobile.model.AppDetails
import com.appcommander.mobile.model.AppEntry
import com.appcommander.mobile.model.CapabilityState
import com.appcommander.mobile.model.ComponentEntry
import com.appcommander.mobile.model.PermissionEntry
import com.appcommander.mobile.privilege.RuntimePermissionSafety
import com.appcommander.mobile.privilege.ShizukuController
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import androidx.core.content.FileProvider

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
                        enabled = ComponentEnabledState.effective(
                            info.enabled,
                            runCatching { pm.getApplicationEnabledSetting(info.packageName) }.getOrNull(),
                        ),
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
        val requested = pkg.requestedPermissions.orEmpty().toList()
        val appEnabled = ComponentEnabledState.effective(
            pkg.applicationInfo?.enabled ?: false,
            runCatching { pm.getApplicationEnabledSetting(entry.packageName) }.getOrNull(),
        )
        // ComponentInfo.enabled may only reflect the manifest default.
        // Re-query the per-component override for every inventory row.
        // A missing/denied state is not proof that a component is enabled.
        fun componentEnabled(name: String, manifestEnabled: Boolean): Boolean =
            appEnabled && ComponentEnabledState.effective(
                manifestEnabled,
                runCatching {
                    pm.getComponentEnabledSetting(ComponentName(entry.packageName, name))
                }.getOrNull(),
            )
        return AppDetails(
            app = entry,
            permissions = requested.mapIndexed { index, name ->
                val granted = (pkg.requestedPermissionsFlags?.getOrNull(index) ?: 0) and
                    PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
                @Suppress("DEPRECATION")
                val protectionLevel = runCatching { pm.getPermissionInfo(name, 0).protectionLevel }.getOrNull()
                PermissionEntry(
                    name, granted,
                    runtimeMutable = RuntimePermissionSafety.canToggle(
                        name, requested, entry.targetSdk, protectionLevel,
                    ),
                )
            }.sortedBy { it.name },
            activities = pkg.activities.orEmpty().map {
                ComponentEntry(
                    it.name, it.exported, componentEnabled(it.name, it.enabled),
                    requiredPermission = it.permission,
                    callerHasPermission = canAccessActivity(it.permission),
                )
            }.sortedBy { it.name },
            services = pkg.services.orEmpty().map {
                ComponentEntry(it.name, it.exported, componentEnabled(it.name, it.enabled))
            }.sortedBy { it.name },
            receivers = pkg.receivers.orEmpty().map {
                ComponentEntry(it.name, it.exported, componentEnabled(it.name, it.enabled))
            }.sortedBy { it.name },
            apkPath = pkg.applicationInfo?.sourceDir.orEmpty(),
        )
    }

    /**
     * Re-query after an elevated command instead of optimistically flipping
     * the old UI state. An unresolvable package is treated as disabled.
     */
    fun isInstalledAppEnabled(packageName: String): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val info = pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        ComponentEnabledState.effective(
            info.enabled,
            pm.getApplicationEnabledSetting(packageName),
        )
    }.getOrDefault(false)

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

    /**
     * ActivityInfo.permission is enforced by Android for cross-app starts.
     * A visible/exported activity may still be inaccessible to our UID.
     * Do not request privileged grants or bypass this check through Shizuku.
     */
    private fun canAccessActivity(permission: String?): Boolean =
        ActivityLaunchSafety.callerHasPermission(permission) { required ->
            context.checkSelfPermission(required) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Activity Launcher uses ordinary Android permissions only. Re-check the
     * component at click time: the list can be stale after an app update,
     * component toggle or package disable. Never attempt private activities
     * through Shizuku or assume startActivity means the target stayed open.
     */
    fun launchActivity(packageName: String, className: String): Boolean {
        if (!ActivityLaunchSafety.validNames(packageName, className)) return false
        return runCatching {
            val component = ComponentName(packageName, className)
            @Suppress("DEPRECATION")
            val activity = pm.getActivityInfo(component, 0)
            val componentEnabled = ComponentEnabledState.effective(
                activity.enabled,
                runCatching { pm.getComponentEnabledSetting(component) }.getOrNull(),
            )
            val appEnabled = ComponentEnabledState.effective(
                activity.applicationInfo.enabled,
                runCatching { pm.getApplicationEnabledSetting(packageName) }.getOrNull(),
            )
            if (!ActivityLaunchSafety.eligible(
                    packageName, className, activity.exported, componentEnabled, appEnabled,
                    canAccessActivity(activity.permission),
                )
            ) return false
            context.startActivity(
                Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

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

    /**
     * Read the installed package and all ordered APK source files as one
     * best-effort identity snapshot. Do not trust a stale UI app entry.
     * PackageManager metadata and file stamps are checked again after the
     * exported SAF document has been independently reopened and verified.
     */
    @Throws(IOException::class)
    private fun readInstalledPackageSnapshot(packageName: String): InstalledPackageSnapshot {
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(packageName, 0)
        val info = installed.applicationInfo
            ?: throw IOException("Installed package has no APK source information")
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty().toList()
        return InstalledPackageSnapshot.capture(
            installed.packageName, installed.longVersionCode, installed.lastUpdateTime, paths,
        ) { path ->
            val file = File(path)
            if (!file.isFile) throw IOException("Installed APK component disappeared")
            InstalledPackageSnapshot.Component(path, file.length(), file.lastModified())
        }
    }

    /**
     * Export only the installed base APK to a user-selected SAF document.
     * A split installation needs its additional split APKs to reinstall; the
     * returned flag ensures the UI never advertises this as a full backup.
     * The destination provider controls its own document and may leave a
     * partial file if an I/O error occurs.
     */
    @Throws(IOException::class)
    internal fun exportInstalledBaseApk(packageName: String, destination: Uri): InstalledApkExport.Result {
        // Only called with the fresh URI returned by CreateDocument. A
        // provider may leave an empty/partial document if reading the source,
        // writing the destination or closing the stream fails.
        return InstalledApkExport.withPartialDocumentCleanup(
            cleanup = {
                if (DocumentsContract.isDocumentUri(context, destination)) {
                    DocumentsContract.deleteDocument(context.contentResolver, destination)
                }
            },
        ) {
            val snapshot = readInstalledPackageSnapshot(packageName)
            val base = snapshot.components.first()
            val written = InstalledApkExport.copyBaseAndVerify(
                openSource = { File(base.path).inputStream() },
                openForWrite = {
                    context.contentResolver.openOutputStream(destination, "wt")
                        ?: throw IOException("Cannot open selected SAF destination")
                },
                openForRead = {
                    context.contentResolver.openInputStream(destination)
                        ?: throw IOException("Cannot reopen exported SAF base APK for verification")
                },
                expectedBytes = base.bytes,
                hasSplits = snapshot.components.size > 1,
            )
            snapshot.requireUnchanged { readInstalledPackageSnapshot(packageName) }
            written
        }
    }

    /**
     * Export all installed APK components (base + splits) as a single SAF ZIP.
     * Unlike Base APK export this preserves the split set, but NOT app data,
     * installer session metadata, signatures verification or a restore action.
     * All paths come from PackageManager; archive entry names are generated.
     */
    @Throws(IOException::class)
    internal fun exportInstalledApkBundle(
        packageName: String, destination: Uri,
    ): InstalledApkBundleExport.Result {
        return InstalledApkExport.withPartialDocumentCleanup(
            cleanup = {
                if (DocumentsContract.isDocumentUri(context, destination)) {
                    DocumentsContract.deleteDocument(context.contentResolver, destination)
                }
            },
        ) {
            val snapshot = readInstalledPackageSnapshot(packageName)
            val sources = snapshot.components.mapIndexed { index, component ->
                val name = if (index == 0) "base.apk" else
                    "split_${index.toString().padStart(3, '0')}.apk"
                InstalledApkBundleExport.Source(name, component.bytes) {
                    File(component.path).inputStream()
                }
            }
            val written = InstalledApkBundleExport.writeAndVerify(
                packageName = packageName,
                sources = sources,
                openForWrite = {
                    context.contentResolver.openOutputStream(destination, "wt")
                        ?: throw IOException("Cannot open selected SAF destination")
                },
                openForRead = {
                    context.contentResolver.openInputStream(destination)
                        ?: throw IOException("Cannot reopen exported SAF document for verification")
                },
            )
            snapshot.requireUnchanged { readInstalledPackageSnapshot(packageName) }
            written
        }
    }

    /** Read a user-selected exported ZIP via SAF; no extraction or installation. */
    @Throws(IOException::class)
    internal fun verifyInstalledApkBundle(uri: Uri): InstalledApkBundleVerifier.Result {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open APK bundle")
        return InstalledApkBundleVerifier.verify(input)
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
            val activityExposure = ApkComponentExposure.count(
                pkg.activities, { it.exported }, { it.permission },
            )
            val serviceExposure = ApkComponentExposure.count(
                pkg.services, { it.exported }, { it.permission },
            )
            val receiverExposure = ApkComponentExposure.count(
                pkg.receivers, { it.exported }, { it.permission },
            )
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
                exportedActivities = activityExposure.exported,
                exportedServices = serviceExposure.exported,
                exportedReceivers = receiverExposure.exported,
                unguardedExportedActivities = activityExposure.withoutDeclaredPermission,
                unguardedExportedServices = serviceExposure.withoutDeclaredPermission,
                unguardedExportedReceivers = receiverExposure.withoutDeclaredPermission,
            )
        }
    }

    /**
     * Best-effort defense against a SAF provider changing its APK after
     * inspection. Read and hash again off the UI thread. This is not an
     * atomic install snapshot; Android's installer independently reopens URI.
     */
    fun matchesInspectedApk(uri: Uri, inspection: ApkInspection): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ApkInstallIntegrity.matches(input, inspection.sizeBytes, inspection.fileSha256)
        } ?: false
    }.getOrDefault(false)

    /**
     * Return a read-only URI for an app-private, byte-for-byte verified copy.
     * Never give the installer the original mutable SAF URI: the provider may
     * otherwise replace the APK between inspection and the installer's read.
     */
    fun prepareVerifiedInstall(uri: Uri, inspection: ApkInspection): Uri? = runCatching {
        val directory = File(context.cacheDir, ApkInstallSnapshot.DIRECTORY)
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        input.use { stream ->
            val file = ApkInstallSnapshot.create(
                directory, stream, inspection.sizeBytes, inspection.fileSha256,
            ) ?: return@runCatching null
            try {
                val verifiedUri = FileProvider.getUriForFile(
                    context, "${context.packageName}.verifiedapks", file,
                )
                ApkInstallSnapshot.cleanupExpired(directory)
                verifiedUri
            } catch (failure: Exception) {
                file.delete()
                throw failure
            }
        }
    }.getOrNull()

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
