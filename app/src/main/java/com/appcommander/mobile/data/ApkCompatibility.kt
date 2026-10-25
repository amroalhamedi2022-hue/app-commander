package com.appcommander.mobile.data

/**
 * A device API level below minSdk blocks ordinary installation. Meeting the
 * minimum is necessary, NOT sufficient: ABI, features, signing, target SDK
 * restrictions and installer policy may still reject an APK.
 */
internal object ApkCompatibility {
    enum class Status { UNKNOWN, BELOW_MINIMUM, MEETS_MINIMUM }
    data class Result(val status: Status, val description: String)

    fun assess(minSdk: Int, deviceSdk: Int): Result = when {
        minSdk <= 0 || deviceSdk <= 0 ->
            Result(Status.UNKNOWN, "لا يمكن تأكيد التوافق: مستوى SDK غير معروف.")
        minSdk > deviceSdk ->
            Result(Status.BELOW_MINIMUM, "غير متوافق مع الحد الأدنى: يتطلب API $minSdk والجهاز API $deviceSdk.")
        else ->
            Result(Status.MEETS_MINIMUM, "الحد الأدنى مناسب (API $deviceSdk)، لكن التثبيت غير مضمون.")
    }
}
