package com.appcommander.mobile.privilege

/**
 * Human-readable, per-operation consent gate for the App Commander UI.
 * Every supported Shizuku change, including recovery actions, needs a fresh
 * explicit confirmation. No consent is persisted or reused after rotation.
 *
 * This is a UI safety gate, not a security boundary against a compromised app:
 * the elevated UserService independently validates each IPC request.
 */
internal object PrivilegedActionConsent {
    private val labels = mapOf(
        PrivilegedOperation.FORCE_STOP to "إيقاف التطبيق إجباريًا",
        PrivilegedOperation.DISABLE_PACKAGE to "تعطيل التطبيق (تجميد)",
        PrivilegedOperation.ENABLE_PACKAGE to "إعادة تفعيل التطبيق",
        PrivilegedOperation.SUSPEND_PACKAGE to "تعليق التطبيق",
        PrivilegedOperation.UNSUSPEND_PACKAGE to "إلغاء تعليق التطبيق",
        PrivilegedOperation.BLOCK_BACKGROUND to "منع عمل التطبيق في الخلفية (إعدادان AppOps)",
        PrivilegedOperation.BLOCK_ANY_BACKGROUND to "منع نشاط الخلفية الإضافي",
        PrivilegedOperation.ALLOW_BACKGROUND to "السماح بعمل التطبيق في الخلفية (إعدادان AppOps)",
        PrivilegedOperation.ALLOW_ANY_BACKGROUND to "السماح بنشاط الخلفية الإضافي",
        PrivilegedOperation.GRANT_PERMISSION to "منح إذن للتطبيق",
        PrivilegedOperation.REVOKE_PERMISSION to "سحب إذن من التطبيق",
        PrivilegedOperation.ENABLE_COMPONENT to "تفعيل مكوّن داخل التطبيق",
        PrivilegedOperation.DISABLE_COMPONENT to "تعطيل مكوّن داخل التطبيق",
    )

    /**
     * Null means deny before even presenting a confirmation dialog.
     * Caller must not execute the action on a null result.
     */
    fun description(operation: Int, packageName: String, argument: String = ""): String? {
        if (PrivilegedRequestValidator.error(operation, packageName, argument) != null) return null
        val label = labels[operation] ?: return null
        val target = if (argument.isEmpty()) packageName else "$packageName\n$argument"
        val warning = if (PrivilegedCommandPolicy.isDestructive(operation)) {
            "\nقد يتوقف التطبيق أو يفقد وظائف مؤقتًا. تأكد من إمكانية استعادته."
        } else {
            "\nقد تتغير صلاحيات التطبيق أو سلوكه. يمكنك الرجوع للإعدادات لاحقًا."
        }
        return "$label؟\n\nالهدف:\n$target$warning\n\nلن ينفذ الأمر إلا بعد موافقتك."
    }
}
