package com.appcommander.mobile.privilege

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PrivilegedProcessRunnerTest {
    private class FakeProcess(
        stdout: String = "", stderr: String = "",
        private val finishes: Boolean = true,
        private val code: Int = 0,
    ) : Process() {
        private val out = ByteArrayInputStream(stdout.toByteArray())
        private val err = ByteArrayInputStream(stderr.toByteArray())
        private val stdin = ByteArrayOutputStream()
        var destroyed = false
            private set
        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = out
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = code
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = finishes || destroyed
        override fun exitValue(): Int = code
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process { destroyed = true; return this }
    }

    @Test fun successfulCommandPreservesExitCodeAndBothStreams() {
        val output = PrivilegedProcessRunner.collect(
            FakeProcess(stdout = "Package enabled\n", stderr = "warning\n", code = 3),
        )
        assertTrue(output.startsWith("exit=3\n"))
        assertTrue(output.contains("Package enabled"))
        assertTrue(output.contains("warning"))
    }

    @Test fun hungCommandIsForciblyTerminatedWithRecognizableTimeout() {
        val process = FakeProcess(finishes = false)
        val result = PrivilegedProcessRunner.collect(process, timeoutSeconds = 1)
        assertTrue(process.destroyed)
        assertTrue(result.startsWith("exit=124\n"))
        assertTrue(result.contains("timed out"))
    }

    @Test fun stdoutAndStderrAreBoundedWithoutBlockingDraining() {
        val result = PrivilegedProcessRunner.collect(
            FakeProcess(stdout = "A".repeat(200_000), stderr = "B".repeat(200_000)),
        )
        assertTrue(result.startsWith("exit=0\n"))
        assertEquals(2, Regex("output truncated at 16 KiB").findAll(result).count())
        assertTrue(result.length < 2 * PrivilegedProcessRunner.MAX_CAPTURE_BYTES + 200)
    }

    @Test fun invalidTimeoutIsRejectedBeforeWaiting() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedProcessRunner.collect(FakeProcess(), timeoutSeconds = 0)
        }
    }
}
