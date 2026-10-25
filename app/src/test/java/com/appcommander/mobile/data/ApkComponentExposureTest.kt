package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ApkComponentExposureTest {
    private data class Item(val exported: Boolean, val permission: String?)

    private fun count(items: Array<Item>?) =
        ApkComponentExposure.count(items, { it.exported }, { it.permission })

    @Test fun emptyOrUnknownInventoryHasNoExportedComponents() {
        assertEquals(ApkComponentExposure.Counts(0, 0), count(null))
        assertEquals(ApkComponentExposure.Counts(0, 0), count(emptyArray()))
    }

    @Test fun countsExportedWithoutConfusingPrivateOrGuardedComponents() {
        val items = arrayOf(
            Item(true, null), Item(false, null),
            Item(true, "com.example.permission.SECRET"),
            Item(true, ""), Item(false, "com.example.permission.SECRET"),
        )
        assertEquals(ApkComponentExposure.Counts(3, 2), count(items))
    }

    @Test fun malformedPermissionCannotBeTreatedAsGuarded() {
        val items = arrayOf(
            Item(true, " "), Item(true, "\t"),
            Item(true, " com.example.PRIVATE"),
            Item(true, "com.example.PRIVATE "),
            Item(true, "com.example.PRIVATE"),
        )
        assertEquals(ApkComponentExposure.Counts(5, 4), count(items))
    }

    @Test fun countsAreIndependentAcrossComponentKinds() {
        val activities = count(arrayOf(Item(true, null), Item(true, "app.PRIVATE")))
        val services = count(arrayOf(Item(false, null), Item(true, null)))
        val receivers = count(arrayOf(Item(true, "app.PRIVATE")))
        assertEquals(ApkComponentExposure.Counts(2, 1), activities)
        assertEquals(ApkComponentExposure.Counts(1, 1), services)
        assertEquals(ApkComponentExposure.Counts(1, 0), receivers)
    }

    @Test fun repositoryReadsArchiveComponentMetadataAndUiLabelsLimitations() {
        val repo = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        val model = File("src/main/java/com/appcommander/mobile/model/AppModels.kt").readText()
        assertTrue(repo.contains("pkg.activities, { it.exported }, { it.permission }"))
        assertTrue(repo.contains("pkg.services, { it.exported }, { it.permission }"))
        assertTrue(repo.contains("pkg.receivers, { it.exported }, { it.permission }"))
        assertTrue(repo.contains("unguardedExportedReceivers = receiverExposure.withoutDeclaredPermission"))
        assertTrue(ui.contains("مصدّرة دون إذن معلن"))
        assertTrue(ui.contains("لا يثبت إمكانية الوصول الفعلية"))
        assertTrue(model.contains("val exportedActivities: Int = 0"))
    }
}
