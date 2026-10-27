package com.appcommander.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInventoryRowMapperTest {
    private fun candidate(
        packageName: String,
        label: () -> CharSequence? = { "Example app" },
        version: () -> String? = { "1.2.3" },
        updated: () -> Long = { 123L },
        targetSdk: Int = 35,
        system: Boolean = false,
        enabled: Boolean = true,
        usageMillis: Long = 900L,
        lastTimeUsed: Long = 80L,
    ) = AppInventoryCandidate(
        packageName = packageName,
        loadLabel = label,
        loadVersionName = version,
        targetSdk = targetSdk,
        isSystem = system,
        enabled = enabled,
        loadLastUpdateTime = updated,
        usageMillis = usageMillis,
        lastTimeUsed = lastTimeUsed,
    )

    @Test fun metadataFailuresDoNotDropAnInstalledApplicationRow() {
        val rows = AppInventoryRowMapper.mapAll(
            listOf(
                candidate(
                    packageName = "org.example.fallback",
                    label = { throw IllegalStateException("label provider failed") },
                    version = { throw IllegalStateException("package metadata unavailable") },
                    updated = { throw IllegalStateException("timestamp unavailable") },
                ),
                candidate(packageName = "org.example.normal", label = { "Normal app" }),
            ),
        )

        assertEquals(2, rows.size)
        val fallback = rows.single { it.packageName == "org.example.fallback" }
        assertEquals("org.example.fallback", fallback.label)
        assertEquals("—", fallback.versionName)
        assertEquals(0L, fallback.lastUpdateTime)
        assertEquals(900L, fallback.usageMillis)
        assertEquals(80L, fallback.lastTimeUsed)
        assertEquals("Normal app", rows.first().label)
    }

    @Test fun blankMetadataUsesStablePlaceholdersAndTrimsValidText() {
        val rows = AppInventoryRowMapper.mapAll(
            listOf(
                candidate(
                    packageName = "org.example.blank",
                    label = { "   " },
                    version = { "" },
                    updated = { -5L },
                ),
                candidate(
                    packageName = "org.example.trimmed",
                    label = { "  Example label  " },
                    version = { "  4.5  " },
                    updated = { 456L },
                ),
            ),
        )

        val blank = rows.single { it.packageName == "org.example.blank" }
        assertEquals(blank.packageName, blank.label)
        assertEquals("—", blank.versionName)
        assertEquals(0L, blank.lastUpdateTime)

        val trimmed = rows.single { it.packageName == "org.example.trimmed" }
        assertEquals("Example label", trimmed.label)
        assertEquals("4.5", trimmed.versionName)
        assertEquals(456L, trimmed.lastUpdateTime)
    }

    @Test fun mappingPreservesIdentityAndNonMetadataFields() {
        val row = AppInventoryRowMapper.mapAll(
            listOf(
                candidate(
                    packageName = "org.example.system",
                    label = { "System app" },
                    targetSdk = 33,
                    system = true,
                    enabled = false,
                    usageMillis = 1200L,
                    lastTimeUsed = 700L,
                ),
            ),
        ).single()

        assertEquals("org.example.system", row.packageName)
        assertEquals(33, row.targetSdk)
        assertTrue(row.isSystem)
        assertFalse(row.enabled)
        assertEquals(1200L, row.usageMillis)
        assertEquals(700L, row.lastTimeUsed)
    }

    @Test fun invalidBlankPackageIdentityIsExcludedWithoutFailingOtherRows() {
        val rows = AppInventoryRowMapper.mapAll(
            listOf(
                candidate(packageName = ""),
                candidate(packageName = "org.example.valid", label = { "Valid app" }),
            ),
        )

        assertEquals(1, rows.size)
        assertEquals("org.example.valid", rows.single().packageName)
    }

    @Test fun duplicateLabelsHaveStablePackageNameTieBreakRegardlessOfProviderOrder() {
        val candidates = listOf(
            candidate(packageName = "org.example.z", label = { "Shared label" }),
            candidate(packageName = "org.example.a", label = { "shared LABEL" }),
            candidate(packageName = "org.example.m", label = { "SHARED LABEL" }),
        )
        val expected = listOf("org.example.a", "org.example.m", "org.example.z")

        assertEquals(expected, AppInventoryRowMapper.mapAll(candidates).map { it.packageName })
        assertEquals(expected, AppInventoryRowMapper.mapAll(candidates.reversed()).map { it.packageName })
        assertEquals(expected, AppInventoryRowMapper.mapAll(listOf(candidates[1], candidates[0], candidates[2])).map { it.packageName })
    }

    @Test fun rowsAreSortedByResolvedDisplayLabel() {
        val rows = AppInventoryRowMapper.mapAll(
            listOf(
                candidate(packageName = "org.example.z", label = { "Zulu" }),
                candidate(packageName = "org.example.a", label = { "Alpha" }),
                candidate(packageName = "org.example.fallback", label = { null }),
            ),
        )

        assertEquals(listOf("Alpha", "org.example.fallback", "Zulu"), rows.map { it.label })
    }
}
