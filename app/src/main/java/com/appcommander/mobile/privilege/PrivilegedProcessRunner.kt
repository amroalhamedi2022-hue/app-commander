package com.appcommander.mobile.privilege

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Executes ONLY a Process already created from CommandUserService's typed
 * allowlisted argv. Never accepts a shell string or a user-supplied command.
 *
 * A stuck Android pm/am/cmd invocation must not block the Shizuku Binder
 * thread forever; output is drained concurrently and bounded in memory.
 */
internal object PrivilegedProcessRunner {
    const val DEFAULT_TIMEOUT_SECONDS = 15L
    const val MAX_CAPTURE_BYTES = 16 * 1024

    /** Keep Binder failures structured if the allowlisted binary cannot start. */
    fun runAllowed(start: () -> Process): String = try {
        collect(start())
    } catch (_: IOException) {
        "exit=1\nPrivileged command could not be started or read"
    } catch (_: SecurityException) {
        "exit=1\nPrivileged command was denied by the runtime"
    }

    fun collect(process: Process, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): String {
        require(timeoutSeconds in 1..120)
        val stdout = BoundedDrain(process.inputStream)
        val stderr = BoundedDrain(process.errorStream)
        val outThread = thread(start = true, isDaemon = true, name = "privileged-stdout") { stdout.readAll() }
        val errThread = thread(start = true, isDaemon = true, name = "privileged-stderr") { stderr.readAll() }
        var completed = false
        try {
            // None of the allowlisted pm/am/appops commands requires stdin.
            runCatching { process.outputStream.close() }
            completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
            outThread.join(1500)
            errThread.join(1500)
            val exitCode = if (completed) process.exitValue() else 124
            return buildString {
                append("exit=").append(exitCode).append('\n')
                if (!completed) append("Privileged command timed out and was terminated").append('\n')
                stdout.snapshot().takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
                stderr.snapshot().takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
            }.trimEnd()
        } finally {
            if (!completed) process.destroyForcibly()
        }
    }

    private class BoundedDrain(private val stream: InputStream) {
        private val capture = ByteArrayOutputStream()
        private var truncated = false

        fun readAll() {
            try {
                stream.use {
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        synchronized(this) {
                            val room = MAX_CAPTURE_BYTES - capture.size()
                            if (room > 0) capture.write(buffer, 0, minOf(room, count))
                            if (count > room) truncated = true
                        }
                        // Continue draining even after the cap: avoids child
                        // blocking on a full stdout/stderr pipe.
                    }
                }
            } catch (_: Exception) {
                // A timed-out process may close pipes while being killed.
            }
        }

        @Synchronized fun snapshot(): String = buildString {
            append(capture.toString(Charsets.UTF_8.name()).trimEnd())
            if (truncated) append("\n[output truncated at 16 KiB]")
        }
    }
}
