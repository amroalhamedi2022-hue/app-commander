package com.appcommander.mobile.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.appcommander.mobile.privilege.CommandResult

class DeviceOwnerController(context: Context) {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, AppCommanderDeviceAdminReceiver::class.java)

    fun isOwner(): Boolean = dpm.isDeviceOwnerApp(appContext.packageName)

    fun setSuspended(packageName: String, suspended: Boolean): CommandResult = runCatching {
        check(isOwner()) { "App Commander is not Device Owner" }
        val failed = dpm.setPackagesSuspended(admin, arrayOf(packageName), suspended)
        if (failed.isEmpty()) CommandResult(true, if (suspended) "Suspended" else "Unsuspended")
        else CommandResult(false, "System refused: ${failed.joinToString()}")
    }.getOrElse { CommandResult(false, it.message ?: "Device Owner command failed") }

    fun setHidden(packageName: String, hidden: Boolean): CommandResult = runCatching {
        check(isOwner()) { "App Commander is not Device Owner" }
        val ok = dpm.setApplicationHidden(admin, packageName, hidden)
        CommandResult(ok, if (ok) (if (hidden) "Hidden" else "Visible") else "System refused")
    }.getOrElse { CommandResult(false, it.message ?: "Device Owner command failed") }
}
