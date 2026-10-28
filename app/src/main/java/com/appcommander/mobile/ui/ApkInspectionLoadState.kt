package com.appcommander.mobile.ui

import com.appcommander.mobile.model.ApkInspection

/**
 * Latest-request-wins state for the local APK inspector.
 *
 * Starting a new inspection clears the previously inspected APK before the
 * new URI can be used for install. Cancellation is not a parse failure, and a
 * late completion from an older request cannot restore a stale result.
 */
internal data class ApkInspectionLoadState(
    val generation: Long = 0L,
    val result: ApkInspection? = null,
    val loading: Boolean = false,
    val error: String? = null,
) {
    fun begin(): ApkInspectionLoadState = copy(
        generation = generation + 1L,
        result = null,
        loading = true,
        error = null,
    )

    fun complete(requestGeneration: Long, inspection: ApkInspection): ApkInspectionLoadState =
        if (requestGeneration != generation) this
        else copy(result = inspection, loading = false, error = null)

    fun fail(requestGeneration: Long, message: String): ApkInspectionLoadState =
        if (requestGeneration != generation) this
        else copy(
            result = null,
            loading = false,
            error = message.ifBlank { "تعذّر فحص الملف" },
        )

    fun cancel(requestGeneration: Long): ApkInspectionLoadState =
        if (requestGeneration != generation) this
        else copy(result = null, loading = false, error = null)
}
