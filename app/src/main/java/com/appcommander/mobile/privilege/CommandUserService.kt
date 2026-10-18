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

    override fun execute(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
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
}
