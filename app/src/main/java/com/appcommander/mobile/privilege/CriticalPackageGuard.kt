package com.appcommander.mobile.privilege

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Protects only packages that are essential to keeping Android usable.
 * Non-critical system packages keep the same controls as user applications.
 */
object CriticalPackageGuard {
    fun reason(context: Context, packageName: String): String? {
        if (packageName == context.packageName) return "App Commander محمي حتى لا يعطّل نفسه"
        if (PrivilegedCommandPolicy.isProtected(packageName, context.packageName)) return "هذا مكوّن نظام حساس ومحمي من الأوامر الخطرة"

        val pm = context.packageManager
        // resolveActivity() can return Android's resolver instead of a real
        // default launcher. Query all HOME candidates and protect every one.
        val homePackages = runCatching {
            @Suppress("DEPRECATION")
            val candidates = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
            )
            candidates.mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrNull()
        val inputPackage = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return CriticalTargetSafety.reason(packageName, homePackages, inputPackage)
    }
}
