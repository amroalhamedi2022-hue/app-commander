package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ComponentEnabledStateTest {
    @Test fun defaultFollowsManifestForEnabledAndDisabledComponents() {
        assertTrue(ComponentEnabledState.effective(true, ComponentEnabledState.DEFAULT))
        assertFalse(ComponentEnabledState.effective(false, ComponentEnabledState.DEFAULT))
    }

    @Test fun explicitEnabledOverridesDisabledManifest() {
        assertTrue(ComponentEnabledState.effective(false, ComponentEnabledState.ENABLED))
    }

    @Test fun allDisabledOverridesDenyEvenManifestEnabled() {
        for (setting in listOf(
            ComponentEnabledState.DISABLED,
            ComponentEnabledState.DISABLED_USER,
            ComponentEnabledState.DISABLED_UNTIL_USED,
        )) {
            assertFalse(ComponentEnabledState.effective(true, setting))
        }
    }

    @Test fun unavailableOrUnknownPackageManagerStateFailsClosed() {
        assertFalse(ComponentEnabledState.effective(true, null))
        assertFalse(ComponentEnabledState.effective(true, -1))
        assertFalse(ComponentEnabledState.effective(true, 999))
    }

    @Test fun disabledParentAppBlocksExplicitlyEnabledComponent() {
        assertFalse(ComponentEnabledState.available(
            manifestComponentEnabled = true,
            componentSetting = ComponentEnabledState.ENABLED,
            manifestAppEnabled = true,
            appSetting = ComponentEnabledState.DISABLED_USER,
        ))
        assertTrue(ComponentEnabledState.available(
            manifestComponentEnabled = true,
            componentSetting = ComponentEnabledState.ENABLED,
            manifestAppEnabled = true,
            appSetting = ComponentEnabledState.ENABLED,
        ))
    }

    @Test fun protectedComponentOnlyOffersRecoveryWhenParentEnabled() {
        assertFalse(ComponentEnabledState.mayToggleComponent(true, true, true))
        assertTrue(ComponentEnabledState.mayToggleComponent(false, true, true))
        assertFalse(ComponentEnabledState.mayToggleComponent(false, false, true))
        assertFalse(ComponentEnabledState.mayToggleComponent(true, false, false))
        assertTrue(ComponentEnabledState.mayToggleComponent(true, true, false))
    }

    @Test fun installedInventoryAndLaunchRequeryOverrides() {
        val source = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        assertTrue(source.contains("pm.getApplicationEnabledSetting(info.packageName)"))
        assertTrue(source.contains("pm.getApplicationEnabledSetting(entry.packageName)"))
        assertTrue(source.contains("pm.getComponentEnabledSetting(ComponentName(entry.packageName, name))"))
        assertEquals(3, Regex("componentEnabled\\(it.name, it.enabled\\)").findAll(source).count())
        assertTrue(source.contains("ComponentEnabledState.effective(\n                activity.enabled,"))
        assertTrue(source.contains("fun isInstalledAppEnabled(packageName: String)"))
    }

    @Test fun uiDoesNotOfferIneffectiveComponentActionsWhileParentFrozen() {
        val source = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        assertEquals(3, source.split(
            "ComponentEnabledState.mayToggleComponent(component.enabled, currentEnabled, protected)"
        ).size - 1)
        assertTrue(source.contains("onClick = if (currentEnabled && component.callerHasPermission) ({"))
        assertTrue(source.contains("repository.isInstalledAppEnabled(entry.packageName)"))
    }
}
