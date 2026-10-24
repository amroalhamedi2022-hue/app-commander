package com.appcommander.mobile.privilege

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
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
        private val interrupted: Boolean = false,
        private val stdoutOverride: InputStream? = null,
    ) : Process() {
        private val out = ByteArrayInputStream(stdout.toByteArray())
        private val err = ByteArrayInputStream(stderr.toByteArray())
        private val stdin = ByteArrayOutputStream()
        var destroyed = false
            private set
        override fun getOutputStream(): OutputStream = stdin
        override fun getInputStream(): InputStream = stdoutOverride ?: out
        override fun getErrorStream(): InputStream = err
        override fun waitFor(): Int = code
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            if (interrupted) throw InterruptedException("private interruption detail")
            return finishes || destroyed
        }
        override fun exitValue(): Int = code
        override fun destroy() { destroyed = true }
        override fun destroyForcibly(): Process { destroyed = true; return this }
    }

    @Test fun permanentlyZeroLengthPipeReadCannotSpinForever() {
        val brokenPipe = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        val result = PrivilegedProcessRunner.collect(FakeProcess(stdoutOverride = brokenPipe))
        assertTrue("A stalled privileged stream must fail closed", result.startsWith("exit=1"))
        assertTrue(result.contains("[output stream stalled]"))
    }

    @Test fun occasionalZeroLengthPipeReadsDoNotLoseFollowingOutput() {
        val pipe = object : InputStream() {
            var calls = 0
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                calls++
                if (calls <= 2) return 0
                if (calls == 3) {
                    b[off] = 'O'.code.toByte()
                    b[off + 1] = 'K'.code.toByte()
                    return 2
                }
                return -1
            }
        }
        val result = PrivilegedProcessRunner.collect(FakeProcess(stdoutOverride = pipe))
        assertTrue(result.contains("OK"))
        assertFalse(result.contains("output stream stalled"))
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

    @Test fun missingAllowlistedBinaryReturnsFailureWithoutBinderException() {
        val result = PrivilegedProcessRunner.runAllowed {
            throw IOException("executable not found")
        }
        assertTrue(result.startsWith("exit=1\n"))
        assertTrue(result.contains("could not be started"))
        assertFalse(result.contains("executable not found"))
    }

    @Test fun runtimeSecurityDenialReturnsFailureWithoutLeakingDetails() {
        val result = PrivilegedProcessRunner.runAllowed {
            throw SecurityException("sensitive runtime detail")
        }
        assertTrue(result.startsWith("exit=1\n"))
        assertTrue(result.contains("denied"))
        assertFalse(result.contains("sensitive runtime detail"))
    }

    @Test fun successfulAllowedProcessStillReportsItsRealExitCode() {
        val result = PrivilegedProcessRunner.runAllowed { FakeProcess(stdout = "ok", code = 7) }
        assertTrue(result.startsWith("exit=7\n"))
        assertTrue(result.contains("ok"))
    }


    @Test fun interruptedCommandIsKilledAndReturnsStructuredFailure() {
        val process = FakeProcess(interrupted = true)
        try {
            val result = PrivilegedProcessRunner.runAllowed { process }
            assertTrue(process.destroyed)
            assertTrue(result.startsWith("exit=1\n"))
            assertTrue(result.contains("interrupted"))
            assertFalse(result.contains("private interruption detail"))
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            // Do not leak the test's deliberate interrupt into JUnit.
            Thread.interrupted()
        }
    }

    @Test fun invalidTimeoutIsRejectedBeforeWaiting() {
        assertThrows(IllegalArgumentException::class.java) {
            PrivilegedProcessRunner.collect(FakeProcess(), timeoutSeconds = 0)
        }
    }

    @Test fun successfulExitWithBrokenStdoutMustFailClosed() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("sensitive pipe failure")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("sensitive pipe failure")
        }
        val result = PrivilegedProcessRunner.collect(FakeProcess(stdoutOverride = broken))
        assertTrue(result.startsWith("exit=1"))
        assertTrue(result.contains("[output stream read failed]"))
        assertFalse(result.contains("sensitive pipe failure"))
    }

    @Test fun partialOutputThenReadFailureIsNeverReportedAsSuccess() {
        val partial = object : InputStream() {
            private var first = true
            override fun read(): Int = -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (first) {
                    first = false
                    b[off] = 'O'.code.toByte()
                    b[off + 1] = 'K'.code.toByte()
                    return 2
                }
                throw IOException("provider failed after partial data")
            }
        }
        val result = PrivilegedProcessRunner.collect(FakeProcess(stdoutOverride = partial))
        assertTrue(result.startsWith("exit=1"))
        assertTrue(result.contains("OK"))
        assertTrue(result.contains("[output stream read failed]"))
        assertFalse(result.contains("provider failed after partial data"))
    }
}
