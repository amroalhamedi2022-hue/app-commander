package com.appcommander.mobile.data

/**
 * Offline, explainable indicators based only on APK manifest metadata.
 *
 * These are review prompts, NOT a malware scan, signature verification, or
 * a declaration that an APK is safe/unsafe. No file content leaves the device.
 */
object ApkInspectionSignals {
    data class Finding(val code: String, val description: String)

    const val DISCLAIMER =
        "هذه مؤشرات من بيانات APK فقط؛ ليست فحصًا للبرمجيات الخبيثة أو تحققًا تشفيريًا من صحة التوقيع. " +
        "وجود بصمة شهادة لا يثبت أن الحزمة آمنة. راجع المصدر قبل التثبيت."

    private val permissionIndicators = linkedMapOf(
        "android.permission.REQUEST_INSTALL_PACKAGES" to
            "يطلب إمكانية تثبيت تطبيقات أخرى بعد موافقة النظام.",
        "android.permission.SYSTEM_ALERT_WINDOW" to
            "يطلب عرض نوافذ فوق التطبيقات الأخرى.",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to
            "يطلب الوصول الواسع لملفات الجهاز.",
        "android.permission.QUERY_ALL_PACKAGES" to
            "يطلب رؤية قائمة التطبيقات المثبتة.",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to
            "يعلن عن صلاحية مرتبطة بخدمات إمكانية الوصول؛ يلزم مراجعة الخدمة نفسها.",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to
            "يعلن عن صلاحية مرتبطة بقراءة الإشعارات.",
        "android.permission.READ_SMS" to
            "يطلب قراءة الرسائل النصية.",
        "android.permission.RECEIVE_SMS" to
            "يطلب استقبال الرسائل النصية.",
        "android.permission.READ_CONTACTS" to
            "يطلب قراءة جهات الاتصال.",
        "android.permission.RECORD_AUDIO" to
            "يطلب تسجيل الصوت من الميكروفون.",
        "android.permission.CAMERA" to
            "يطلب استخدام كاميرا الجهاز.",
        "android.permission.ACCESS_FINE_LOCATION" to
            "يطلب الوصول إلى الموقع الجغرافي الدقيق.",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to
            "يطلب الوصول إلى الموقع حتى عندما لا يكون التطبيق ظاهرًا؛ يعتمد المنح على إصدار Android وموافقة المستخدم.",
        "android.permission.SEND_SMS" to
            "يطلب إرسال رسائل SMS؛ قد يترتب على الإرسال رسوم من المشغّل.",
        "android.permission.READ_CALL_LOG" to
            "يطلب قراءة سجل المكالمات؛ قد تقيّد المنصة منح هذا الإذن.",
        "android.permission.READ_PHONE_NUMBERS" to
            "يطلب قراءة أرقام الهاتف المرتبطة بالجهاز.",
        "android.permission.READ_CALENDAR" to
            "يطلب قراءة أحداث التقويم.",
        "android.permission.WRITE_CONTACTS" to
            "يطلب تعديل جهات الاتصال.",
        "android.permission.READ_MEDIA_IMAGES" to
            "يطلب قراءة الصور المخزنة؛ الوصول الفعلي يخضع لأذونات النظام.",
        "android.permission.READ_MEDIA_VIDEO" to
            "يطلب قراءة الفيديوهات المخزنة؛ الوصول الفعلي يخضع لأذونات النظام.",
    )

    fun evaluate(targetSdk: Int, permissions: Collection<String>, signerSha256: List<String>): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (targetSdk <= 0) {
            findings += Finding("target_unknown", "تعذّر تحديد إصدار Android المستهدف.")
        } else if (targetSdk < 29) {
            findings += Finding("legacy_target", "يستهدف Android API $targetSdk القديم؛ راجع توافق الأذونات والحماية.")
        }
        if (signerSha256.isEmpty()) {
            findings += Finding("signer_unavailable", "لم تُستخرج بصمة شهادة التوقيع من بيانات الحزمة.")
        }
        val declared = permissions.toSet()
        for ((permission, description) in permissionIndicators) {
            if (permission in declared) findings += Finding(permission, description)
        }
        // Combined capabilities deserve a separate, explainable review prompt.
        // This is manifest-only analysis; permissions may never be granted.
        if ("android.permission.READ_SMS" in declared && "android.permission.SEND_SMS" in declared) {
            findings += Finding("sms_read_send_combination",
                "يطلب قراءة رسائل SMS وإرسالها معًا؛ راجع ضرورة الجمع بين الصلاحيتين.")
        }
        if ("android.permission.ACCESS_FINE_LOCATION" in declared &&
            "android.permission.ACCESS_BACKGROUND_LOCATION" in declared) {
            findings += Finding("precise_background_location_combination",
                "يطلب الموقع الدقيق والموقع في الخلفية معًا؛ راجع سبب الحاجة للتتبع المستمر.")
        }
        return findings
    }
}
