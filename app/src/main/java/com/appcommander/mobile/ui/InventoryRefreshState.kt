package com.appcommander.mobile.ui

import com.appcommander.mobile.model.AppEntry

/**
 * Pure, latest-request-wins state for the installed-app inventory.
 *
 * A failed refresh must not discard the last successful list. Every completion
 * is tagged with the generation returned by [begin], so slow older requests
 * cannot overwrite newer results or hide their loading/error state.
 */
internal data class InventoryRefreshState(
    val generation: Long = 0L,
    val apps: List<AppEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    fun begin(): InventoryRefreshState = copy(
        generation = generation + 1L,
        loading = true,
        error = null,
    )

    fun complete(requestGeneration: Long, loadedApps: List<AppEntry>): InventoryRefreshState =
        if (requestGeneration != generation) this
        else copy(apps = loadedApps.toList(), loading = false, error = null)

    fun fail(requestGeneration: Long, message: String): InventoryRefreshState =
        if (requestGeneration != generation) this
        else copy(
            loading = false,
            error = message.ifBlank { "تعذّر تحديث قائمة التطبيقات. جرّب مرة أخرى." },
        )

    /** Cancellation is not a user-visible failure and must not clear cached apps. */
    fun cancel(requestGeneration: Long): InventoryRefreshState =
        if (requestGeneration != generation) this
        else copy(loading = false)
}
