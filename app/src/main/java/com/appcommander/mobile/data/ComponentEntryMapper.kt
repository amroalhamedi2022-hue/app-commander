package com.appcommander.mobile.data

import com.appcommander.mobile.model.ComponentEntry

/** Maps installed manifest component metadata without losing the declared access gate. */
internal object ComponentEntryMapper {
    fun <T> map(
        components: Array<T>?,
        name: (T) -> String,
        exported: (T) -> Boolean,
        enabled: (T) -> Boolean,
        requiredPermission: (T) -> String?,
        callerHasPermission: (String?) -> Boolean,
    ): List<ComponentEntry> = components.orEmpty().map { component ->
        val permission = requiredPermission(component)
        ComponentEntry(
            name = name(component),
            exported = exported(component),
            enabled = enabled(component),
            requiredPermission = permission,
            callerHasPermission = callerHasPermission(permission),
        )
    }.sortedBy { it.name }
}
