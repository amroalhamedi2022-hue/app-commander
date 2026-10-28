package com.appcommander.mobile.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentEntryMapperTest {
    private data class Component(
        val name: String,
        val exported: Boolean,
        val enabled: Boolean,
        val permission: String?,
    )

    @Test fun preservesPermissionGateAndCallerAvailabilityForAllComponentKinds() {
        val mapped = ComponentEntryMapper.map(
            components = arrayOf(
                Component("z.Service", true, true, "org.example.permission.HELD"),
                Component("a.Activity", true, false, null),
                Component("m.Receiver", false, true, "org.example.permission.SIGNATURE"),
            ),
            name = { it.name },
            exported = { it.exported },
            enabled = { it.enabled },
            requiredPermission = { it.permission },
            callerHasPermission = { permission ->
                permission == null || permission == "org.example.permission.HELD"
            },
        )

        assertEquals(listOf("a.Activity", "m.Receiver", "z.Service"), mapped.map { it.name })
        val activity = mapped[0]
        assertEquals(null, activity.requiredPermission)
        assertTrue(activity.callerHasPermission)
        assertFalse(activity.enabled)

        val receiver = mapped[1]
        assertEquals("org.example.permission.SIGNATURE", receiver.requiredPermission)
        assertFalse(receiver.callerHasPermission)
        assertFalse(receiver.exported)

        val service = mapped[2]
        assertEquals("org.example.permission.HELD", service.requiredPermission)
        assertTrue(service.callerHasPermission)
        assertTrue(service.enabled)
    }

    @Test fun blankPermissionIsNormalizedBeforeCallerPermissionLookup() {
        val checkedPermissions = mutableListOf<String?>()
        val mapped = ComponentEntryMapper.map(
            components = arrayOf(
                Component("a.Activity", exported = true, enabled = true, permission = "  \\t "),
            ),
            name = { it.name },
            exported = { it.exported },
            enabled = { it.enabled },
            requiredPermission = { it.permission },
            callerHasPermission = { permission ->
                checkedPermissions.add(permission)
                permission == null
            },
        )

        assertEquals(listOf<String?>(null), checkedPermissions)
        assertEquals(1, mapped.size)
        assertEquals(null, mapped.single().requiredPermission)
        assertTrue(mapped.single().callerHasPermission)
    }

    @Test fun nullComponentArrayProducesAnEmptyInventory() {
        val mapped = ComponentEntryMapper.map<Component>(
            components = null,
            name = { it.name },
            exported = { it.exported },
            enabled = { it.enabled },
            requiredPermission = { it.permission },
            callerHasPermission = { it == null },
        )
        assertTrue(mapped.isEmpty())
    }

    @Test fun repositoryAndComponentRowsKeepPermissionGateVisible() {
        val repository = File("src/main/java/com/appcommander/mobile/data/AppRepository.kt").readText()
        val ui = File("src/main/java/com/appcommander/mobile/ui/AppCommanderApp.kt").readText()
        assertTrue(repository.contains("activities = ComponentEntryMapper.map("))
        assertTrue(repository.contains("services = ComponentEntryMapper.map("))
        assertTrue(repository.contains("receivers = ComponentEntryMapper.map("))
        assertTrue(ui.contains("requiredPermission?.takeIf { it.isNotBlank() }"))
        assertTrue(ui.contains("إذن: \$permission"))
        assertTrue(ui.contains("callerHasPermission = component.callerHasPermission"))
    }
}
