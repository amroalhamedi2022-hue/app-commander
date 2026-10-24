package com.appcommander.mobile.privilege

import java.util.concurrent.CancellationException

/**
 * Keep UI actions recoverable if a privileged command unexpectedly throws.
 * Coroutine cancellation is NOT a command failure: propagate it so closing
 * the screen or cancelling its scope cannot keep background work alive.
 * Do not display exception messages from a privileged process to the UI.
 */
internal object SafeCommandCall {
    suspend fun execute(block: suspend () -> CommandResult): CommandResult =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CommandResult(false, "Command failed")
        }
}
