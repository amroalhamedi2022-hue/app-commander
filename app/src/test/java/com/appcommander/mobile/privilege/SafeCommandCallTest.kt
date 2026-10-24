package com.appcommander.mobile.privilege

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SafeCommandCallTest {
    @Test fun successfulResultIsPreserved() = runBlocking {
        assertEquals(
            CommandResult(true, "done"),
            SafeCommandCall.execute { CommandResult(true, "done") },
        )
    }

    @Test fun commandRejectionIsNotMisreportedAsSuccess() = runBlocking {
        assertEquals(
            CommandResult(false, "permission denied"),
            SafeCommandCall.execute { CommandResult(false, "permission denied") },
        )
    }

    @Test fun binderFailureBecomesGenericFailureWithoutLeakingException() = runBlocking {
        val result = SafeCommandCall.execute {
            throw IOException("sensitive binder details /data/user/0/private")
        }
        assertFalse(result.success)
        assertEquals("Command failed", result.output)
    }

    @Test fun cancellationPropagatesInsteadOfBeingConvertedToFailure() = runBlocking<Unit> {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                SafeCommandCall.execute {
                    throw CancellationException("screen closed")
                }
            }
        }
    }
}
