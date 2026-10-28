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
    private const val MAX_EMPTY_READS = 8

    /** Keep Binder failures structured if the allowlisted binary cannot start. */
    fun runAllowed(start: () -> Process): String = try {
        collect(start())
    } catch (_: IOException) {
        "exit=1\nPrivileged command could not be started or read"
    } catch (_: SecurityException) {
        "exit=1\nPrivileged command was denied by the runtime"
    } catch (_: InterruptedException) {
        // Binder worker cancellation must not leave a privileged pm/am process
        // running or leak a Java exception across IPC. collect() kills it in
        // finally; restore the interrupted flag for the calling thread.
        Thread.currentThread().interrupt()
        "exit=1\nPrivileged command interrupted"
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
            // A process can exit while a pipe remains open (for example, an
            // inherited descriptor or a broken provider stream). Do not leave
            // a daemon reader parked forever after returning to the Binder UI.
            // Mark the capture incomplete BEFORE closing so it can never turn
            // into a false exit=0 merely because close releases the reader.
            if (outThread.isAlive) stdout.closeAfterStall()
            if (errThread.isAlive) stderr.closeAfterStall()
            outThread.join(250)
            errThread.join(250)
            // A stalled pipe means the output was not completely observed.
            // Do not report a privileged command as successful on partial I/O.
            val exitCode = when {
                !completed -> 124
                stdout.isStalled() || stderr.isStalled() ||
                    stdout.hasReadFailure() || stderr.hasReadFailure() ||
                    outThread.isAlive || errThread.isAlive -> 1
                else -> process.exitValue()
            }
            return buildString {
                append("exit=").append(exitCode).append('\n')
                if (!completed) append("Privileged command timed out and was terminated").append('\n')
                stdout.snapshot().takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
                stderr.snapshot().takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
            }.trimEnd()
        } finally {
            if (!completed) process.destroyForcibly()
            if (outThread.isAlive) stdout.closeAfterStall()
            if (errThread.isAlive) stderr.closeAfterStall()
        }
    }

    private class BoundedDrain(private val stream: InputStream) {
        private val capture = ByteArrayOutputStream()
        private var truncated = false
        private var stalled = false
        private var readFailed = false

        fun readAll() {
            var emptyReads = 0
            try {
                stream.use {
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        if (count == 0) {
                            // A broken process pipe can return zero forever.
                            // Bound retries instead of spinning a daemon at 100% CPU.
                            if (++emptyReads >= MAX_EMPTY_READS) {
                                synchronized(this) { stalled = true }
                                break
                            }
                            continue
                        }
                        emptyReads = 0
                        synchronized(this) {
                            val room = MAX_CAPTURE_BYTES - capture.size()
                            if (room > 0) capture.write(buffer, 0, minOf(room, count))
                            if (count > room) truncated = true
                        }
                        // Continue draining even after the cap: avoids child
                        // blocking on a full stdout/stderr pipe.
                    }
                }
            } catch (failure: Exception) {
                // A stream can fail even when the command exits with code 0.
                // Never report success when stdout/stderr was only partially
                // observed. Do not expose exception details across Binder.
                synchronized(this) { readFailed = true }
                if (failure is InterruptedException) Thread.currentThread().interrupt()
            }
        }

        fun closeAfterStall() {
            synchronized(this) { stalled = true }
            runCatching { stream.close() }
        }

        @Synchronized fun isStalled(): Boolean = stalled

        @Synchronized fun hasReadFailure(): Boolean = readFailed

        @Synchronized fun snapshot(): String = buildString {
            append(capture.toString(Charsets.UTF_8.name()).trimEnd())
            if (truncated) append("\n[output truncated at 16 KiB]")
            if (stalled) append("\n[output stream stalled]")
            if (readFailed) append("\n[output stream read failed]")
        }
    }
}
