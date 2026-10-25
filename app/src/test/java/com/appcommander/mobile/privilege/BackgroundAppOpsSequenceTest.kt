package com.appcommander.mobile.privilege

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackgroundAppOpsSequenceTest {
    @Test fun firstFailureSkipsSecondMutation() = runBlocking {
        var calls = 0
        val result = BackgroundAppOpsSequence.apply("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "OK",
            first = { calls++; CommandResult(false, "permission denied") },
            second = { calls++; CommandResult(true, "") },
        )
        assertFalse(result.success)
        assertEquals(1, calls)
        assertTrue(result.output.contains("لم نحاول"))
        assertTrue(result.output.contains("permission denied"))
    }

    @Test fun secondFailureReportsPartialStateWithoutUnsafeRollback() = runBlocking {
        var calls = 0
        val result = BackgroundAppOpsSequence.apply("RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND", "OK",
            first = { calls++; CommandResult(true, "") },
            second = { calls++; CommandResult(false, "unsupported") },
        )
        assertFalse(result.success)
        assertEquals(2, calls)
        assertTrue(result.output.contains("تغيير جزئي"))
        assertTrue(result.output.contains("يدويًا"))
        assertTrue(result.output.contains("unsupported"))
    }

    @Test fun successRequiresBothOperations() = runBlocking {
        var calls = 0
        val result = BackgroundAppOpsSequence.apply("first", "second", "Background blocked",
            first = { calls++; CommandResult(true, "") },
            second = { calls++; CommandResult(true, "") },
        )
        assertTrue(result.success)
        assertEquals(2, calls)
        assertEquals("Background blocked", result.output)
    }
}
