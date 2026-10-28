package com.appcommander.mobile.data

import java.io.IOException

/**
 * Best-effort installed-package identity fence for SAF exports.
 *
 * A package can be updated or replaced while its base/split APK files are
 * being streamed. Matching the saved ZIP to the bytes we read does NOT prove
 * that those bytes still describe the installed package at completion.
 *
 * Capture PackageManager identity, ordered source paths and file metadata
 * before export; require the same snapshot AFTER closing and verifying the
 * destination. This is not an atomic OS snapshot or APK signature check.
 */
internal data class InstalledPackageSnapshot(
    val packageName: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val components: List<Component>,
) {
    data class Component(val path: String, val bytes: Long, val modifiedAt: Long)

    companion object {
        @Throws(IOException::class)
        fun capture(
            packageName: String,
            versionCode: Long,
            lastUpdateTime: Long,
            paths: List<String>,
            stat: (String) -> Component,
        ): InstalledPackageSnapshot {
            if (packageName.isBlank() || versionCode < 0 || lastUpdateTime < 0 ||
                paths.isEmpty() || paths.size > InstalledApkBundleExport.MAX_APK_COUNT
            ) throw IOException("Invalid installed package snapshot")

            val seen = HashSet<String>()
            var total = 0L
            val components = paths.map { path ->
                if (path.isBlank() || !seen.add(path)) {
                    throw IOException("Missing or repeated installed APK source")
                }
                val component = stat(path)
                if (component.path != path ||
                    component.bytes !in 1..ApkCacheCopy.MAX_APK_BYTES ||
                    component.modifiedAt < 0 ||
                    component.bytes > InstalledApkBundleExport.MAX_TOTAL_BYTES - total
                ) throw IOException("Invalid installed APK component metadata")
                total += component.bytes
                component
            }
            return InstalledPackageSnapshot(packageName, versionCode, lastUpdateTime, components)
        }
    }

    /** Fail closed on package removal, metadata failure or any changed component. */
    @Throws(IOException::class)
    fun requireUnchanged(readCurrent: () -> InstalledPackageSnapshot) {
        val current = try {
            readCurrent()
        } catch (failure: Exception) {
            throw IOException("Cannot confirm installed package after APK export", failure)
        }
        if (current != this) {
            throw IOException("Installed package changed during APK export; saved document is not verified")
        }
    }
}
