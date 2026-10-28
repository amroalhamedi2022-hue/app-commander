package com.appcommander.mobile.ui

import com.appcommander.mobile.data.InstalledApkExport
import java.util.concurrent.CancellationException

/**
 * User-facing outcomes for SAF exports. Cancellation is still rethrown by the
 * caller; these messages are surfaced only when cleanup of a fresh partial
 * document could not be confirmed.
 */
internal object SafExportFailureMessage {
    fun baseApkFailure(failure: Throwable): String =
        if (InstalledApkExport.cleanupWasUnconfirmed(failure)) {
            "تعذّر تصدير APK أو فشل التحقق من الملف المحفوظ. لم يتأكد حذف الملف الجزئي؛ افحص المجلد واحذفه يدويًا إن بقي."
        } else {
            "تعذّر تصدير APK أو فشل التحقق من الملف المحفوظ. تم تأكيد حذف الملف الجزئي."
        }

    fun bundleFailure(failure: Throwable): String =
        if (InstalledApkExport.cleanupWasUnconfirmed(failure)) {
            "تعذّر تصدير حزمة APKs. لم يتأكد حذف ملف ZIP الجزئي؛ افحص المجلد واحذفه يدويًا إن بقي."
        } else {
            "تعذّر تصدير حزمة APKs. تم تأكيد حذف ملف ZIP الجزئي."
        }

    fun baseApkCancellation(failure: CancellationException): String =
        if (InstalledApkExport.cleanupWasUnconfirmed(failure)) {
            "تم إلغاء التصدير، لكن لم يتأكد حذف الملف الجزئي؛ افحص المجلد واحذفه يدويًا إن بقي."
        } else {
            "تم إلغاء تصدير APK؛ لم يُعتمد الملف كنسخة مكتملة."
        }

    fun bundleCancellation(failure: CancellationException): String =
        if (InstalledApkExport.cleanupWasUnconfirmed(failure)) {
            "تم إلغاء تصدير حزمة APKs، لكن لم يتأكد حذف ملف ZIP الجزئي؛ افحص المجلد واحذفه يدويًا إن بقي."
        } else {
            "تم إلغاء تصدير حزمة APKs؛ لم يُعتمد ملف ZIP كنسخة مكتملة."
        }
}
