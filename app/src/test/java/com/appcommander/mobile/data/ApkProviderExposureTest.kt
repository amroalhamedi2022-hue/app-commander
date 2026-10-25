package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ApkProviderExposureTest {
    private data class Provider(
        val exported: Boolean,
        val read: String? = null,
        val write: String? = null,
        val grants: Boolean = false,
    )

    private fun count(items: Array<Provider>?) = ApkComponentExposure.countProviders(
        items, { it.exported }, { it.read }, { it.write }, { it.grants },
    )

    @Test fun nullAndEmptyProviderArraysAreSafe() {
        val none = ApkComponentExposure.ProviderCounts(0, 0, 0, 0)
        assertEquals(none, count(null))
        assertEquals(none, count(emptyArray()))
    }

    @Test fun readAndWriteGatesAreIndependent() {
        val result = count(arrayOf(
            Provider(true, "app.READ", "app.WRITE"),
            Provider(true, read = "app.READ"),
            Provider(true, write = "app.WRITE"),
            Provider(true),
        ))
        assertEquals(ApkComponentExposure.ProviderCounts(4, 2, 2, 0), result)
    }

    @Test fun nonExportedProviderIsNotCountedAsPublic() {
        val result = count(arrayOf(
            Provider(false), Provider(true, "app.READ", "app.WRITE"),
        ))
        assertEquals(ApkComponentExposure.ProviderCounts(1, 0, 0, 0), result)
    }

    @Test fun privateProviderCanStillGrantSpecificUris() {
        val result = count(arrayOf(
            Provider(false, grants = true),
            Provider(true, "app.READ", "app.WRITE", grants = true),
        ))
        assertEquals(ApkComponentExposure.ProviderCounts(1, 0, 0, 2), result)
    }

    @Test fun exportedProviderWithNoDeclaredGatesIsReportedInBothDirections() {
        assertEquals(
            ApkComponentExposure.ProviderCounts(1, 1, 1, 0),
            count(arrayOf(Provider(true))),
        )
    }

    @Test fun providerInventoryIsConnectedToAndroidArchiveAndUi() {
        val repo = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        val model = File("src/main/java/com/appcommander/mobile/model/AppModels.kt").readText()
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        assertTrue(repo.contains("PackageManager.GET_PROVIDERS"))
        assertTrue(repo.contains("pkg.providers, { it.exported }, { it.readPermission }"))
        assertTrue(repo.contains("{ it.writePermission }, { it.grantUriPermissions }"))
        assertTrue(repo.contains("providersGrantUriPermissions = providerExposure.uriGrantCapable"))
        assertTrue(model.contains("val exportedProviders: Int = 0"))
        assertTrue(model.contains("val providersWithoutReadPermission: Int = 0"))
        assertTrue(ui.contains("Providers مصدّرة دون إذن قراءة معلن"))
        assertTrue(ui.contains("Providers مصدّرة دون إذن كتابة معلن"))
        assertTrue(ui.contains("أذونات المسارات ومنح URI"))
    }
}
