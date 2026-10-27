package com.appcommander.mobile.ui

import com.appcommander.mobile.model.AppDetails

/**
 * Latest-request-wins state for the app-detail pane.
 * Failed refreshes preserve the last readable snapshot; an initial failure
 * stops the spinner and exposes a retryable error instead of loading forever.
 */
internal data class AppDetailsLoadState(
    val generation: Long = 0L,
    val details: AppDetails? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    fun begin(): AppDetailsLoadState = copy(
        generation = generation + 1L,
        loading = true,
        error = null,
    )

    fun complete(requestGeneration: Long, loaded: AppDetails): AppDetailsLoadState =
        if (requestGeneration != generation) this
        else copy(details = loaded, loading = false, error = null)

    fun fail(requestGeneration: Long, message: String): AppDetailsLoadState =
        if (requestGeneration != generation) this
        else copy(
            loading = false,
            error = message.ifBlank { "تعذّر تحميل تفاصيل التطبيق. جرّب مرة أخرى." },
        )

    /** Cancellation is not an error and cannot overwrite a newer request. */
    fun cancel(requestGeneration: Long): AppDetailsLoadState =
        if (requestGeneration != generation) this
        else copy(loading = false)
}
