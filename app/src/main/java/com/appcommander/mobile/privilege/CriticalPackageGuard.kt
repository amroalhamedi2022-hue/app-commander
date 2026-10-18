package com.appcommander.mobile.privilege

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Protects only packages that are essential to keeping Android usable.
 * Non-critical system packages keep the same controls as user applications.
 */
object CriticalPackageGuard {
    private val fixedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.miui.securitycenter",
        "com.lbe.security.miui",
        "com.miui.powerkeeper",
        "com.miui.home",
        "moe.shizuku.privileged.api",
    )

    fun reason(context: Context, packageName: String): String? {
        if (packageName == context.packageName) return "App Commander محمي حتى لا يعطّل نفسه"
        if (packageName in fixedPackages) return "هذا مكوّن نظام حساس ومحمي من الأوامر الخطرة"

        val pm = context.packageManager
        val homePackage = runCatching {
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0,
            )?.activityInfo?.packageName
        }.getOrNull()
        if (packageName == homePackage) return "اللانشر الافتراضي محمي حتى تبقى واجهة الجهاز قابلة للاستخدام"

        val inputPackage = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()
        if (packageName == inputPackage) return "لوحة المفاتيح الافتراضية محمية"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permissionController = runCatching { pm.permissionControllerPackageName }.getOrNull()
            if (packageName == permissionController) return "مدير أذونات النظام محمي"
        }
        return null
    }
}
