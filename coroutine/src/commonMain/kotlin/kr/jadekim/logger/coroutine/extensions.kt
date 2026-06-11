package kr.jadekim.logger.coroutine

import kotlinx.coroutines.withContext
import kr.jadekim.logger.JLogger
import kr.jadekim.logger.LogExtra
import kr.jadekim.logger.LogLevel
import kr.jadekim.logger.context.LogContext
import kr.jadekim.logger.coroutine.context.CoroutineLogContext

suspend fun JLogger.sLog(
    level: LogLevel,
    message: String,
    throwable: Throwable? = null,
    meta: Map<String, Any?> = emptyMap(),
    context: LogContext? = null,
) {
    val coroutineLogContext = CoroutineLogContext.get().snap()

    log(level, message, throwable, meta, coroutineLogContext + context)
}

suspend fun JLogger.sLog(level: LogLevel, log: LogExtra.() -> String) {
    if (!level.isPrintableAt(this.level)) {
        return
    }

    val extra = LogExtra()
    val message = log(extra)

    sLog(level, message, extra.throwable, extra.meta, extra.context)
}

suspend fun JLogger.sFetal(message: String, throwable: Throwable? = null, meta: Map<String, Any?> = emptyMap()) {
    sLog(LogLevel.FETAL, message, throwable, meta)
}

suspend fun JLogger.sFetal(body: LogExtra.() -> String) {
    sLog(LogLevel.FETAL, body)
}

suspend fun JLogger.sError(message: String, throwable: Throwable? = null, meta: Map<String, Any?> = emptyMap()) {
    sLog(LogLevel.ERROR, message, throwable, meta)
}

suspend fun JLogger.sError(body: LogExtra.() -> String) {
    sLog(LogLevel.ERROR, body)
}

suspend fun JLogger.sWarning(message: String, throwable: Throwable? = null, meta: Map<String, Any?> = emptyMap()) {
    sLog(LogLevel.WARNING, message, throwable, meta)
}

suspend fun JLogger.sWarning(body: LogExtra.() -> String) {
    sLog(LogLevel.WARNING, body)
}

suspend fun JLogger.sInfo(message: String, throwable: Throwable? = null, meta: Map<String, Any?> = emptyMap()) {
    sLog(LogLevel.INFO, message, throwable, meta)
}

suspend fun JLogger.sInfo(body: LogExtra.() -> String) {
    sLog(LogLevel.INFO, body)
}

suspend fun JLogger.sDebug(message: String, throwable: Throwable? = null, meta: Map<String, Any?> = emptyMap()) {
    sLog(LogLevel.DEBUG, message, throwable, meta)
}

suspend fun JLogger.sDebug(body: LogExtra.() -> String) {
    sLog(LogLevel.DEBUG, body)
}

suspend fun JLogger.sTrace(message: String, throwable: Throwable? = null, meta: Map<String, Any?> = emptyMap()) {
    sLog(LogLevel.TRACE, message, throwable, meta)
}

suspend fun JLogger.sTrace(body: LogExtra.() -> String) {
    sLog(LogLevel.TRACE, body)
}

suspend fun <T> withLogContext(context: Map<String, Any?>? = null, body: suspend () -> T): T {
    var logContext = CoroutineLogContext.get()

    if (context != null) {
        logContext = CoroutineLogContext(logContext.snap() + LogContext(context))
    }

    return withContext(logContext) {
        body()
    }
}
