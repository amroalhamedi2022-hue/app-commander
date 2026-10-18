package com.appcommander.mobile.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.appcommander.mobile.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

data class CommandResult(val success: Boolean, val output: String)

object ShizukuController {
    const val REQUEST_CODE = 9411

    private var args: Shizuku.UserServiceArgs? = null
    @Volatile private var remote: ICommandService? = null
    @Volatile private var binding = false
    private var pending = CompletableDeferred<ICommandService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = ICommandService.Stub.asInterface(service)
            remote = connected
            binding = false
            if (!pending.isCompleted) pending.complete(connected)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remote = null
            binding = false
            pending = CompletableDeferred()
        }
    }

    fun initialize(context: Context) {
        if (args != null) return
        args = Shizuku.UserServiceArgs(ComponentName(context.packageName, CommandUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("commander")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = isRunning() && runCatching {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        if (isRunning() && !hasPermission()) Shizuku.requestPermission(REQUEST_CODE)
    }

    suspend fun forceStop(packageName: String) = exec("am force-stop --user 0 ${safe(packageName)}")
    suspend fun freeze(packageName: String) = exec("pm disable-user --user 0 ${safe(packageName)}")
    suspend fun unfreeze(packageName: String) = exec("pm enable --user 0 ${safe(packageName)}")
    suspend fun suspend(packageName: String) = exec("pm suspend --user 0 ${safe(packageName)}")
    suspend fun unsuspend(packageName: String) = exec("pm unsuspend --user 0 ${safe(packageName)}")

    suspend fun blockBackground(packageName: String): CommandResult {
        val pkg = safe(packageName)
        val first = exec("cmd appops set --user 0 $pkg RUN_IN_BACKGROUND ignore")
        val second = exec("cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND ignore")
        return combine(first, second, "Background blocked")
    }

    suspend fun allowBackground(packageName: String): CommandResult {
        val pkg = safe(packageName)
        val first = exec("cmd appops set --user 0 $pkg RUN_IN_BACKGROUND allow")
        val second = exec("cmd appops set --user 0 $pkg RUN_ANY_IN_BACKGROUND allow")
        return combine(first, second, "Background allowed")
    }

    suspend fun setPermission(packageName: String, permission: String, grant: Boolean): CommandResult {
        val verb = if (grant) "grant" else "revoke"
        return exec("pm $verb --user 0 ${safe(packageName)} ${safe(permission)}")
    }

    suspend fun setComponent(packageName: String, componentName: String, enabled: Boolean): CommandResult {
        val verb = if (enabled) "enable --user 0" else "disable-user --user 0"
        return exec("pm $verb ${safe(packageName)}/${safe(componentName)}")
    }

    private suspend fun exec(command: String): CommandResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CommandResult(false, "Shizuku permission is not available")
        runCatching {
            val reply = obtainService().execute(command)
            val firstLine = reply.lineSequence().firstOrNull().orEmpty()
            val success = firstLine == "exit=0"
            CommandResult(success, reply.lineSequence().drop(1).joinToString("\n").trim())
        }.getOrElse {
            remote = null
            CommandResult(false, it.message ?: "Command failed")
        }
    }

    private suspend fun obtainService(): ICommandService {
        remote?.let { if (it.asBinder().pingBinder()) return it }
        val serviceArgs = requireNotNull(args) { "ShizukuController.initialize must be called first" }
        synchronized(this) {
            if (!binding) {
                binding = true
                pending = CompletableDeferred()
                Shizuku.bindUserService(serviceArgs, connection)
            }
        }
        return withTimeout(8_000) { pending.await() }
    }

    private fun combine(first: CommandResult, second: CommandResult, successText: String): CommandResult {
        return if (first.success && second.success) CommandResult(true, successText)
        else CommandResult(false, listOf(first.output, second.output).filter { it.isNotBlank() }.joinToString("\n"))
    }

    private fun safe(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.$]+"))) { "Unsafe package or component name" }
        return value
    }
}
