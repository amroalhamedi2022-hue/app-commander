package com.appcommander.mobile.privilege

/**
 * A pair of AppOps writes is NOT atomic. Never silently run the second
 * command after the first fails, or claim both succeeded after a partial
 * failure. We deliberately do not roll back to "allow": the original modes
 * may have been default/foreground/ignore and are not known here.
 */
internal object BackgroundAppOpsSequence {
    suspend fun apply(
        firstName: String,
        secondName: String,
        successText: String,
        first: suspend () -> CommandResult,
        second: suspend () -> CommandResult,
    ): CommandResult {
        val firstResult = first()
        if (!firstResult.success) {
            return CommandResult(
                false,
                "$firstName فشل؛ لم نحاول تغيير $secondName. ${firstResult.output}".trim(),
            )
        }
        val secondResult = second()
        if (!secondResult.success) {
            return CommandResult(
                false,
                "تغيير جزئي: تم تغيير $firstName لكن فشل $secondName. " +
                    "لا نفترض الوضع السابق ولا نرجع الإعدادات تلقائيًا؛ راجع إعدادات الخلفية يدويًا. " +
                    secondResult.output,
            )
        }
        return CommandResult(true, successText)
    }
}
