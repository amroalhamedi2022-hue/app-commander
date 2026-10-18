package com.appcommander.mobile.privilege

import android.content.Context
import androidx.annotation.Keep
import kotlin.concurrent.thread

@Keep
class CommandUserService() : ICommandService.Stub() {
    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun execute(operation: Int, packageName: String, argument: String): String {
        val pkg = safe(packageName)
        val arg = argument.takeIf { it.isNotBlank() }?.let(::safe)
        val command = when (operation) {
            PrivilegedOperation.FORCE_STOP -> arrayOf("am", "force-stop", "--user", "0", pkg)
            PrivilegedOperation.DISABLE_PACKAGE -> arrayOf("pm", "disable-user", "--user", "0", pkg)
            PrivilegedOperation.ENABLE_PACKAGE -> arrayOf("pm", "enable", "--user", "0", pkg)
            PrivilegedOperation.SUSPEND_PACKAGE -> arrayOf("pm", "suspend", "--user", "0", pkg)
            PrivilegedOperation.UNSUSPEND_PACKAGE -> arrayOf("pm", "unsuspend", "--user", "0", pkg)
            PrivilegedOperation.BLOCK_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_IN_BACKGROUND", "ignore")
            PrivilegedOperation.BLOCK_ANY_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_ANY_IN_BACKGROUND", "ignore")
            PrivilegedOperation.ALLOW_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_IN_BACKGROUND", "allow")
            PrivilegedOperation.ALLOW_ANY_BACKGROUND -> arrayOf("cmd", "appops", "set", "--user", "0", pkg, "RUN_ANY_IN_BACKGROUND", "allow")
            PrivilegedOperation.GRANT_PERMISSION -> arrayOf("pm", "grant", "--user", "0", pkg, requireNotNull(arg))
            PrivilegedOperation.REVOKE_PERMISSION -> arrayOf("pm", "revoke", "--user", "0", pkg, requireNotNull(arg))
            PrivilegedOperation.ENABLE_COMPONENT -> arrayOf("pm", "enable", "--user", "0", "$pkg/${requireNotNull(arg)}")
            PrivilegedOperation.DISABLE_COMPONENT -> arrayOf("pm", "disable-user", "--user", "0", "$pkg/${requireNotNull(arg)}")
            else -> error("Unsupported privileged operation")
        }
        val process = Runtime.getRuntime().exec(command)
        var stdout = ""
        var stderr = ""
        val outThread = thread(start = true, name = "command-stdout") {
            stdout = process.inputStream.bufferedReader().use { it.readText() }
        }
        val errThread = thread(start = true, name = "command-stderr") {
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        }
        val code = process.waitFor()
        outThread.join()
        errThread.join()
        return buildString {
            append("exit=").append(code).append('\n')
            if (stdout.isNotBlank()) append(stdout.trim()).append('\n')
            if (stderr.isNotBlank()) append(stderr.trim())
        }.trim()
    }

    private fun safe(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_.$]+"))) { "Unsafe command argument" }
        return value
    }
}
