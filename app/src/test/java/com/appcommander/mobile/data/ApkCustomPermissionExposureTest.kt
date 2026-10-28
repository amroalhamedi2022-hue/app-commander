package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkCustomPermissionExposureTest {
    private data class Component(val exported: Boolean, val permission: String?)
    private data class Provider(
        val exported: Boolean,
        val read: String?,
        val write: String?,
        val paths: List<ApkCustomPermissionExposure.PathPermissionGate> = emptyList(),
    )

    @Test fun onlyExportedComponentsUsingOwnNormalOrDangerousPermissionsAreCounted() {
        val ownNonSignature = setOf("app.permission.NORMAL", "app.permission.DANGEROUS")
        val count = ApkCustomPermissionExposure.countComponents(
            components = arrayOf(
                Component(true, "app.permission.NORMAL"),
                Component(true, "app.permission.DANGEROUS"),
                Component(true, "app.permission.SIGNATURE"),
                Component(true, "android.permission.READ_SMS"),
                Component(false, "app.permission.NORMAL"),
                Component(true, null),
            ),
            exported = { it.exported },
            requiredPermission = { it.permission },
            nonSignatureAppPermissions = ownNonSignature,
        )
        assertEquals(2, count)
    }

    @Test fun providerDirectionsAndPathOverridesUseTheirEffectivePermission() {
        val ownNonSignature = setOf("app.permission.NORMAL", "app.permission.DANGEROUS")
        val result = ApkCustomPermissionExposure.countProviders(
            providers = arrayOf(
                Provider(
                    exported = true,
                    read = "app.permission.NORMAL",
                    write = "app.permission.SIGNATURE",
                    paths = listOf(
                        ApkCustomPermissionExposure.PathPermissionGate(null, null),
                        ApkCustomPermissionExposure.PathPermissionGate("app.permission.DANGEROUS", null),
                    ),
                ),
                Provider(
                    exported = true,
                    read = "android.permission.READ_SMS",
                    write = "app.permission.DANGEROUS",
                    paths = listOf(ApkCustomPermissionExposure.PathPermissionGate(null, null)),
                ),
                Provider(
                    exported = false,
                    read = "app.permission.NORMAL",
                    write = "app.permission.DANGEROUS",
                    paths = listOf(ApkCustomPermissionExposure.PathPermissionGate(null, null)),
                ),
            ),
            exported = { it.exported },
            readPermission = { it.read },
            writePermission = { it.write },
            pathPermissions = { it.paths },
            nonSignatureAppPermissions = ownNonSignature,
        )
        assertEquals(2, result.directionsUsingNonSignatureAppPermission)
        assertEquals(3, result.pathRulesUsingNonSignatureAppPermission)
    }

    @Test fun emptyPermissionSetProducesNoCounts() {
        val provider = arrayOf(Provider(true, "app.permission.NORMAL", "app.permission.NORMAL"))
        assertEquals(
            ApkCustomPermissionExposure.ProviderCounts(0, 0),
            ApkCustomPermissionExposure.countProviders(
                providers = provider,
                exported = { it.exported },
                readPermission = { it.read },
                writePermission = { it.write },
                pathPermissions = { it.paths },
                nonSignatureAppPermissions = emptySet(),
            ),
        )
        assertEquals(
            0,
            ApkCustomPermissionExposure.countComponents(
                components = arrayOf(Component(true, "app.permission.NORMAL")),
                exported = { it.exported },
                requiredPermission = { it.permission },
                nonSignatureAppPermissions = emptySet(),
            ),
        )
    }

    @Test fun repositoryModelAndInspectorUiAreConnected() {
        val repo = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        val signals = File("src/main/java/com/appcommander/mobile/data/ApkInspectionSignals.kt").readText()
        val model = File("src/main/java/com/appcommander/mobile/model/AppModels.kt").readText()
        assertTrue(repo.contains("PermissionInfo.PROTECTION_MASK_BASE"))
        assertTrue(repo.contains("PermissionInfo.PROTECTION_DANGEROUS"))
        assertTrue(repo.contains("ApkCustomPermissionExposure.countComponents"))
        assertTrue(repo.contains("ApkCustomPermissionExposure.countProviders"))
        assertTrue(repo.contains("exportedComponentsUsingNonSignatureAppPermission = customPermissionComponentCount"))
        assertTrue(ui.contains("exportedComponentsUsingNonSignatureAppPermission = apk.exportedComponentsUsingNonSignatureAppPermission"))
        assertTrue(signals.contains("exported_gates_with_non_signature_app_permission"))
        assertTrue(model.contains("val providerDirectionsUsingNonSignatureAppPermission: Int = 0"))
    }
}
