package com.appcommander.mobile.data

import com.appcommander.mobile.model.ComponentEntry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ActivityLaunchSafetyTest {
    @Test fun onlyPublicEnabledActivitiesAreEligible() {
        val pkg = "com.example.app"
        val name = "com.example.app.MainActivity"
        assertTrue(ActivityLaunchSafety.eligible(pkg, name, true, true, true))
        assertFalse(ActivityLaunchSafety.eligible(pkg, name, false, true, true))
        assertFalse(ActivityLaunchSafety.eligible(pkg, name, true, false, true))
        assertFalse(ActivityLaunchSafety.eligible(pkg, name, true, true, false))
    }

    @Test fun rejectsMalformedNamesAndAllowsNestedJavaClass() {
        assertTrue(ActivityLaunchSafety.validNames("com.example.app", "org.example.Screen\$Nested"))
        for (bad in listOf("", "com.example/Bad", "com.example.Bad;am", "com.example. Bad", "com.example.\nBad")) {
            assertFalse(ActivityLaunchSafety.validNames("com.example.app", bad))
        }
        assertFalse(ActivityLaunchSafety.validNames("com.example/evil", "com.example.Main"))
        assertFalse(ActivityLaunchSafety.validNames("com.example.app", "x".repeat(513)))
    }

    @Test fun searchAndExportedFilterDoNotMutateInventory() {
        val all = listOf(
            ComponentEntry("com.example.PublicScreen", true, true),
            ComponentEntry("com.example.PrivateScreen", false, true),
            ComponentEntry("com.example.DisabledScreen", true, false),
        )
        assertEquals(2, ActivityLaunchSafety.visible(all, "", true).size)
        assertEquals(listOf(all[1]), ActivityLaunchSafety.visible(all, "private", false))
        assertTrue(ActivityLaunchSafety.visible(all, "private", true).isEmpty())
        assertEquals(listOf(all[0]), ActivityLaunchSafety.visible(all, " PUBLIC ", true))
        assertEquals(3, all.size)
    }

    @Test fun repositoryAndUiRequireFreshPackageManagerChecksAndHonestFeedback() {
        val repo = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        assertTrue(repo.contains("pm.getActivityInfo(component, 0)"))
        assertTrue(repo.contains("pm.getComponentEnabledSetting(component)"))
        assertTrue(repo.contains("pm.getApplicationEnabledSetting(packageName)"))
        assertTrue(repo.contains("activity.exported"))
        assertTrue(repo.contains("ActivityLaunchSafety.eligible("))
        assertTrue(ui.contains("ActivityLaunchSafety.visible("))
        assertTrue(ui.contains("repository.launchActivity(entry.packageName, component.name)"))
        assertTrue(ui.contains("تعذّر فتح"))
        assertTrue(ui.contains("لا يؤكد ذلك"))
    }
}
