package com.appcommander.mobile.privilege

/**
 * Reject malformed privileged IPC requests before checking Shizuku or binding
 * its UserService. The elevated CommandUserService repeats validation; this
 * first gate avoids unnecessary Binder work and makes the no-dispatch contract
 * directly testable on the JVM.
 */
internal object PrivilegedCommandPreflight {
    suspend fun execute(
        operation: Int,
        packageName: String,
        argument: String,
        dispatch: suspend () -> CommandResult,
    ): CommandResult {
        val error = PrivilegedRequestValidator.error(operation, packageName, argument)
        if (error != null) return CommandResult(false, error)
        return dispatch()
    }
}
