package kr.jadekim.logger.integration

import kr.jadekim.logger.JLog
import kr.jadekim.logger.LogData
import kr.jadekim.logger.LogLevel
import kr.jadekim.logger.context.LogContext
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import kotlin.time.Instant

class JulLogger : Handler() {

    companion object {

        @JvmStatic
        private fun getRootLogger(): java.util.logging.Logger {
            return LogManager.getLogManager().getLogger("")
        }

        @JvmStatic
        fun install() {
            LogManager.getLogManager().getLogger("").addHandler(JulLogger())
        }

        @JvmStatic
        @Throws(SecurityException::class)
        fun uninstall() {
            val rootLogger = getRootLogger()
            rootLogger.handlers
                .filterIsInstance<JulLogger>()
                .forEach { rootLogger.removeHandler(it) }
        }

        @JvmStatic
        fun isInstalled(): Boolean {
            return getRootLogger().handlers.any { it is JulLogger }
        }

        @JvmStatic
        fun removeHandlersForRootLogger() {
            val rootLogger = getRootLogger()

            rootLogger.handlers.forEach { rootLogger.removeHandler(it) }
        }
    }

    private val logger = JLog.get("JulLogger")

    private val Level.jlogLevel: LogLevel?
        get() = when (this) {
            Level.OFF -> LogLevel.NONE
            Level.SEVERE -> LogLevel.ERROR
            Level.WARNING -> LogLevel.WARNING
            Level.INFO -> LogLevel.INFO
            Level.CONFIG, Level.FINE -> LogLevel.DEBUG
            Level.ALL, Level.FINER, Level.FINEST -> LogLevel.TRACE
            else -> null
        }

    override fun publish(record: LogRecord?) {
        val level = record?.level?.jlogLevel ?: return
        val threadName =
            Thread.getAllStackTraces().asIterable().find { it.key.id == record.threadID.toLong() }?.key?.name

        logger.log(
            LogData(
                record.loggerName,
                level,
                record.message,
                record.thrown,
                record.parameters?.withIndex()?.associate { it.index.toString() to it.value } ?: emptyMap(),
                LogContext(
                    mapOf(
                        "threadId" to record.threadID,
                        "sourceClassName" to record.sourceClassName,
                        "sourceMethodName" to record.sourceMethodName,
                        "sequenceNumber" to record.sequenceNumber,
                    ),
                ),
                threadName,
                Instant.fromEpochMilliseconds(record.millis),
            )
        )
    }

    override fun flush() {
        //do nothing
    }

    override fun close() {
        //do nothing
    }
}