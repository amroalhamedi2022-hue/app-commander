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

        return null
    }
}
