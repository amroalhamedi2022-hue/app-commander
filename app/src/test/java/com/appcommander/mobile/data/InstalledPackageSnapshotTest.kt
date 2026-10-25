package com.appcommander.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class InstalledPackageSnapshotTest {
    private val base = "/data/app/example/base.apk"
    private val split = "/data/app/example/split_config.arm64_v8a.apk"

    private fun snapshot(
        version: Long = 42,
        updated: Long = 1000,
        paths: List<String> = listOf(base, split),
        sizes: Map<String, Long> = mapOf(base to 200L, split to 100L),
    ) = InstalledPackageSnapshot.capture("com.example.app", version, updated, paths) { path ->
        InstalledPackageSnapshot.Component(path, sizes.getValue(path), 12345L)
    }

    @Test fun unchangedInstalledBaseAndSplitsAreAccepted() {
        val before = snapshot()
        before.requireUnchanged { snapshot() }
        assertEquals(2, before.components.size)
        assertEquals(base, before.components.first().path)
    }

    @Test fun versionUpdateOrReinstallFailsClosed() {
        val before = snapshot()
        for (changed in listOf(snapshot(version = 43), snapshot(updated = 1001))) {
            val error = assertThrows(IOException::class.java) {
                before.requireUnchanged { changed }
            }
            assertTrue(error.message.orEmpty().contains("changed during APK export"))
        }
    }

    @Test fun changedSplitInventoryOrderAndFileStampsFailClosed() {
        val before = snapshot()
        val alternatives = listOf(
            snapshot(paths = listOf(base)),
            snapshot(paths = listOf(split, base)),
            snapshot(sizes = mapOf(base to 200L, split to 99L)),
            before.copy(components = before.components.mapIndexed { index, item ->
                if (index == 1) item.copy(modifiedAt = item.modifiedAt + 1) else item
            }),
        )
        alternatives.forEach { changed ->
            assertThrows(IOException::class.java) { before.requireUnchanged { changed } }
        }
    }

    @Test fun unavailablePackageOnRecheckFailsClosedWithOriginalCause() {
        val before = snapshot()
        val error = assertThrows(IOException::class.java) {
            before.requireUnchanged { throw IllegalStateException("package removed") }
        }
        assertEquals("package removed", error.cause?.message)
    }

    @Test fun malformedOrRepeatedSourcePathsAreRejectedBeforeExport() {
        for (paths in listOf(emptyList(), listOf(base, base), listOf(""))) {
            assertThrows(IOException::class.java) {
                InstalledPackageSnapshot.capture("com.example.app", 1, 1, paths) { path ->
                    InstalledPackageSnapshot.Component(path, 1, 0)
                }
            }
        }
        assertThrows(IOException::class.java) {
            InstalledPackageSnapshot.capture("com.example.app", 1, 1, listOf(base)) {
                InstalledPackageSnapshot.Component(it, ApkCacheCopy.MAX_APK_BYTES + 1, 0)
            }
        }
    }

    @Test fun changedPackageDuringVerifiedBaseExportTriggersDocumentCleanup() {
        val bytes = ByteArray(8192) { it.toByte() }
        val output = ByteArrayOutputStream()
        val before = snapshot(paths = listOf(base), sizes = mapOf(base to bytes.size.toLong()))
        var cleaned = false
        assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(cleanup = { cleaned = true }) {
                val written = InstalledApkExport.copyBaseAndVerify(
                    openSource = { ByteArrayInputStream(bytes) },
                    openForWrite = { output },
                    openForRead = { ByteArrayInputStream(output.toByteArray()) },
                    expectedBytes = bytes.size.toLong(),
                    hasSplits = false,
                )
                assertEquals(bytes.size.toLong(), written.bytes)
                before.requireUnchanged { before.copy(lastUpdateTime = 2000) }
            }
        }
        assertTrue(cleaned)
    }

    @Test fun changedPackageDuringVerifiedSplitBundleTriggersDocumentCleanup() {
        val bytes = ByteArray(4096) { it.toByte() }
        val output = ByteArrayOutputStream()
        val before = snapshot(paths = listOf(base), sizes = mapOf(base to bytes.size.toLong()))
        var cleaned = false
        assertThrows(IOException::class.java) {
            InstalledApkExport.withPartialDocumentCleanup(cleanup = { cleaned = true }) {
                val written = InstalledApkBundleExport.writeAndVerify(
                    packageName = "com.example.app",
                    sources = listOf(InstalledApkBundleExport.Source(
                        "base.apk", bytes.size.toLong(),
                    ) { ByteArrayInputStream(bytes) }),
                    openForWrite = { output },
                    openForRead = { ByteArrayInputStream(output.toByteArray()) },
                )
                assertEquals(bytes.size.toLong(), written.totalApkBytes)
                before.requireUnchanged { before.copy(versionCode = 43) }
            }
        }
        assertTrue(cleaned)
    }

    @Test fun androidRepositoryGuardsBothExportRoutesAfterSafReadback() {
        val code = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        val baseRoute = code.substringAfter("internal fun exportInstalledBaseApk(")
            .substringBefore("internal fun exportInstalledApkBundle(")
        val bundleRoute = code.substringAfter("internal fun exportInstalledApkBundle(")
            .substringBefore("internal fun verifyInstalledApkBundle(")
        for (route in listOf(baseRoute, bundleRoute)) {
            assertTrue(route.contains("val snapshot = readInstalledPackageSnapshot(packageName)"))
            assertTrue(route.contains("snapshot.requireUnchanged { readInstalledPackageSnapshot(packageName) }"))
            assertTrue(route.indexOf("snapshot.requireUnchanged") > route.indexOf("openForRead ="))
            assertTrue(route.contains("withPartialDocumentCleanup"))
        }
    }
}
