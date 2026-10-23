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
    @Volatile private var appContext: Context? = null
    @Volatile private var remote: ICommandService? = null
    @Volatile private var binding = false
    @Volatile private var pending = CompletableDeferred<ICommandService>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = ICommandService.Stub.asInterface(service)
            synchronized(ShizukuController) {
                remote = connected
                binding = false
                if (!pending.isCompleted) pending.complete(connected)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(ShizukuController) {
                remote = null
                binding = false
                if (!pending.isCompleted) {
                    pending.completeExceptionally(IllegalStateException("Shizuku service disconnected"))
                }
                pending = CompletableDeferred()
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
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

    suspend fun forceStop(packageName: String) = destructive(packageName) {
        exec(PrivilegedOperation.FORCE_STOP, packageName)
    }
    suspend fun freeze(packageName: String) = destructive(packageName) {
        exec(PrivilegedOperation.DISABLE_PACKAGE, packageName)
    }
    suspend fun unfreeze(packageName: String) = exec(PrivilegedOperation.ENABLE_PACKAGE, packageName)
    suspend fun suspend(packageName: String) = destructive(packageName) {
        exec(PrivilegedOperation.SUSPEND_PACKAGE, packageName)
    }
    suspend fun unsuspend(packageName: String) = exec(PrivilegedOperation.UNSUSPEND_PACKAGE, packageName)

    suspend fun blockBackground(packageName: String): CommandResult {
        return destructive(packageName) {
            val first = exec(PrivilegedOperation.BLOCK_BACKGROUND, packageName)
            val second = exec(PrivilegedOperation.BLOCK_ANY_BACKGROUND, packageName)
            combine(first, second, "Background blocked")
        }
    }

    suspend fun allowBackground(packageName: String): CommandResult {
        val first = exec(PrivilegedOperation.ALLOW_BACKGROUND, packageName)
        val second = exec(PrivilegedOperation.ALLOW_ANY_BACKGROUND, packageName)
        return combine(first, second, "Background allowed")
    }

    suspend fun setPermission(packageName: String, permission: String, grant: Boolean): CommandResult {
        return if (grant) exec(PrivilegedOperation.GRANT_PERMISSION, packageName, permission)
        else destructive(packageName) { exec(PrivilegedOperation.REVOKE_PERMISSION, packageName, permission) }
    }

    suspend fun setComponent(packageName: String, componentName: String, enabled: Boolean): CommandResult {
        return if (enabled) exec(PrivilegedOperation.ENABLE_COMPONENT, packageName, componentName)
        else destructive(packageName) { exec(PrivilegedOperation.DISABLE_COMPONENT, packageName, componentName) }
    }

    private suspend fun exec(operation: Int, packageName: String, argument: String = ""): CommandResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CommandResult(false, "Shizuku permission is not available")
        runCatching {
            val reply = obtainService().execute(operation, safe(packageName), argument.takeIf { it.isNotBlank() }?.let(::safe).orEmpty())
            val firstLine = reply.lineSequence().firstOrNull().orEmpty()
            val success = firstLine == "exit=0"
            CommandResult(success, reply.lineSequence().drop(1).joinToString("\n").trim())
        }.getOrElse {
            remote = null
            CommandResult(false, it.message ?: "Command failed")
        }
    }

    private suspend fun destructive(packageName: String, block: suspend () -> CommandResult): CommandResult {
        val context = appContext ?: return CommandResult(false, "App Commander is not initialized")
        val reason = CriticalPackageGuard.reason(context, safe(packageName))
        return if (reason != null) CommandResult(false, reason) else block()
    }

    private suspend fun obtainService(): ICommandService {
        remote?.let { if (it.asBinder().pingBinder()) return it }
        val serviceArgs = requireNotNull(args) { "ShizukuController.initialize must be called first" }
        val waiter: CompletableDeferred<ICommandService>
        synchronized(this) {
            if (!binding) {
                binding = true
                pending = CompletableDeferred()
                try {
                    Shizuku.bindUserService(serviceArgs, connection)
                } catch (failure: Exception) {
                    binding = false
                    pending.completeExceptionally(failure)
                }
            }
            // Capture this attempt. Awaiting the mutable global pending could
            // otherwise wait on a different bind after a disconnect/retry.
            waiter = pending
        }
        try {
            return withTimeout(8_000) { waiter.await() }
        } catch (failure: Exception) {
            synchronized(this) {
                // A failed/timeout attempt must not leave binding=true forever.
                // Do not reset a newer attempt or a binder that connected late.
                if (pending === waiter && remote == null) {
                    binding = false
                    pending = CompletableDeferred()
                }
            }
            throw failure
        }
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
