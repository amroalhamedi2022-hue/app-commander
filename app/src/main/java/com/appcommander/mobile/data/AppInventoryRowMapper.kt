package com.appcommander.mobile.data

import com.appcommander.mobile.model.AppEntry

import java.text.Normalizer
import java.util.Locale

/**
 * Maps the PackageManager's installed-application snapshot into display rows.
 *
 * The package list determines membership. Labels, version strings and update
 * timestamps are best-effort metadata and must not silently remove a valid row.
 * This mapper has no Android dependency so failure paths are deterministic in JVM tests.
 */
internal data class AppInventoryCandidate(
    val packageName: String,
    val loadLabel: () -> CharSequence?,
    val loadVersionName: () -> String?,
    val targetSdk: Int,
    val isSystem: Boolean,
    val enabled: Boolean,
    val loadLastUpdateTime: () -> Long,
    val usageMillis: Long,
    val lastTimeUsed: Long,
)

internal object AppInventoryRowMapper {
    private const val UNKNOWN_VERSION = "—"

    fun mapAll(candidates: Iterable<AppInventoryCandidate>): List<AppEntry> =
        candidates.asSequence()
            .filter { it.packageName.isNotBlank() }
            .map(::mapOne)
            .sortedWith(
                compareBy<AppEntry> { inventorySortKey(it.label) }
                    .thenBy { inventorySortKey(it.packageName) }
                    .thenBy { it.packageName },
            )
            .toList()

    /**
     * Sort by a canonical Unicode key while preserving the original display
     * label in AppEntry. NFC makes composed/decomposed accents compare equally;
     * invariant lowercase keeps ordering independent of the device's UI locale.
     */
    private fun inventorySortKey(value: String): String =
        Normalizer.normalize(
            Normalizer.normalize(value, Normalizer.Form.NFC).lowercase(Locale.ROOT),
            Normalizer.Form.NFC,
        )

    private fun mapOne(candidate: AppInventoryCandidate): AppEntry {
        val label = try {
            candidate.loadLabel()?.toString()?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }.ifBlank { candidate.packageName }

        val versionName = try {
            candidate.loadVersionName()?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }.ifBlank { UNKNOWN_VERSION }

        val lastUpdateTime = try {
            candidate.loadLastUpdateTime().coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }

        return AppEntry(
            label = label,
            packageName = candidate.packageName,
            versionName = versionName,
            targetSdk = candidate.targetSdk,
            isSystem = candidate.isSystem,
            enabled = candidate.enabled,
            lastUpdateTime = lastUpdateTime,
            usageMillis = candidate.usageMillis,
            lastTimeUsed = candidate.lastTimeUsed,
        )
    }
}
