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
    private val connectionState = ShizukuConnectionState<ICommandService>()

    /**
     * Each platform callback is bound to one request generation. Old Shizuku
     * callbacks may arrive after timeout/retry; they must never mutate the new
     * waiter's Binder state.
     */
    private fun connectionFor(
        waiter: CompletableDeferred<ICommandService>,
        serviceArgs: Shizuku.UserServiceArgs,
    ): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = ICommandService.Stub.asInterface(service)
            val accepted = synchronized(ShizukuController) {
                connectionState.connectedIfCurrent(waiter, connected)
            }
            if (!accepted) unbind(serviceArgs, this)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(ShizukuController) {
                connectionState.failIfCurrent(waiter, "Shizuku service disconnected")
            }
            unbind(serviceArgs, this)
        }

        override fun onBindingDied(name: ComponentName) {
            synchronized(ShizukuController) {
                connectionState.failIfCurrent(waiter, "Shizuku binding died")
            }
            unbind(serviceArgs, this)
        }

        override fun onNullBinding(name: ComponentName) {
            synchronized(ShizukuController) {
                connectionState.failIfCurrent(waiter, "Shizuku returned no service")
            }
            unbind(serviceArgs, this)
        }
    }

    /** Remove only this callback; never request termination of the UserService. */
    private fun unbind(serviceArgs: Shizuku.UserServiceArgs, connection: ServiceConnection) {
        runCatching { Shizuku.unbindUserService(serviceArgs, connection, false) }
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

    suspend fun blockBackground(packageName: String): CommandResult =
        destructive(packageName) {
            BackgroundAppOpsSequence.apply(
                "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "Background blocked",
                first = { exec(PrivilegedOperation.BLOCK_BACKGROUND, packageName) },
                second = { exec(PrivilegedOperation.BLOCK_ANY_BACKGROUND, packageName) },
            )
        }

    suspend fun allowBackground(packageName: String): CommandResult =
        BackgroundAppOpsSequence.apply(
            "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "Background allowed",
            first = { exec(PrivilegedOperation.ALLOW_BACKGROUND, packageName) },
            second = { exec(PrivilegedOperation.ALLOW_ANY_BACKGROUND, packageName) },
        )

    suspend fun setPermission(packageName: String, permission: String, grant: Boolean): CommandResult {
        return if (grant) exec(PrivilegedOperation.GRANT_PERMISSION, packageName, permission)
        else destructive(packageName) { exec(PrivilegedOperation.REVOKE_PERMISSION, packageName, permission) }
    }

    suspend fun setComponent(packageName: String, componentName: String, enabled: Boolean): CommandResult {
        return if (enabled) exec(PrivilegedOperation.ENABLE_COMPONENT, packageName, componentName)
        else destructive(packageName) { exec(PrivilegedOperation.DISABLE_COMPONENT, packageName, componentName) }
    }

    private suspend fun exec(operation: Int, packageName: String, argument: String = ""): CommandResult =
        PrivilegedCommandPreflight.execute(operation, packageName, argument) {
            withContext(Dispatchers.IO) {
                if (!hasPermission()) return@withContext CommandResult(false, "Shizuku permission is not available")
                var usedService: ICommandService? = null
                try {
                    val service = obtainService()
                    usedService = service
                    val reply = service.execute(operation, safe(packageName), argument.takeIf { it.isNotBlank() }?.let(::safe).orEmpty())
                    val firstLine = reply.lineSequence().firstOrNull().orEmpty()
                    val success = firstLine == "exit=0"
                    CommandResult(success, reply.lineSequence().drop(1).joinToString("\n").trim())
                } catch (cancelled: java.util.concurrent.CancellationException) {
                    // runCatching swallows coroutine cancellation and can leave
                    // stale privileged work running after its UI scope is gone.
                    usedService?.let { service -> synchronized(this) { connectionState.discardIfSame(service) } }
                    throw cancelled
                } catch (_: Exception) {
                    usedService?.let { service -> synchronized(this) { connectionState.discardIfSame(service) } }
                    CommandResult(false, "Command failed")
                }
            }
        }

    private suspend fun destructive(packageName: String, block: suspend () -> CommandResult): CommandResult {
        val packageError = PrivilegedRequestValidator.packageNameError(packageName)
        if (packageError != null) return CommandResult(false, packageError)
        val context = appContext ?: return CommandResult(false, "App Commander is not initialized")
        val reason = CriticalPackageGuard.reason(context, packageName)
        return if (reason != null) CommandResult(false, reason) else block()
    }

    private suspend fun obtainService(): ICommandService {
        val candidate = connectionState.remoteCandidate()
        if (candidate != null) {
            val alive = runCatching { candidate.asBinder().pingBinder() }.getOrDefault(false)
            if (alive) return candidate
            // A dead cached Binder must be removed before timeout recovery.
            // Identity check prevents an old failed ping from clearing a newer
            // ServiceConnection callback that has already installed a live Binder.
            synchronized(this) { connectionState.discardIfSame(candidate) }
        }
        val serviceArgs = requireNotNull(args) { "ShizukuController.initialize must be called first" }
        val request: BindingRequest<ICommandService>
        var attemptConnection: ServiceConnection? = null
        var bindFailedConnection: ServiceConnection? = null
        synchronized(this) {
            request = connectionState.beginOrJoin()
            if (request.shouldBind) {
                val attempt = connectionFor(request.waiter, serviceArgs)
                attemptConnection = attempt
                try {
                    Shizuku.bindUserService(serviceArgs, attempt)
                } catch (_: Exception) {
                    connectionState.failIfCurrent(request.waiter, "Shizuku service bind failed")
                    bindFailedConnection = attempt
                }
            }
        }
        bindFailedConnection?.let { unbind(serviceArgs, it) }
        try {
            return withTimeout(8_000) { request.waiter.await() }
        } catch (failure: Exception) {
            // Only the owner can reset/unbind this attempt. A joiner's own
            // timeout must not cancel a bind that another caller still awaits.
            val reset = synchronized(this) {
                connectionState.resetTimedOutAttempt(request)
            }
            if (reset) attemptConnection?.let { unbind(serviceArgs, it) }
            throw failure
        }
    }

    private fun safe(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.$]+"))) { "Unsafe package or component name" }
        return value
    }
}
