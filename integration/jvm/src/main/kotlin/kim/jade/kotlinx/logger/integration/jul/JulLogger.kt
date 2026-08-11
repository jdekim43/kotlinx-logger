@file:Suppress("unused")

package kim.jade.kotlinx.logger.integration.jul

import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.context.LogContext
import java.util.logging.*
import kotlin.time.Instant
import kim.jade.kotlinx.logger.Logger as KLogger

class JulLogger : Handler() {

    companion object {

        @JvmStatic
        private fun getRootLogger(): Logger {
            return LogManager.getLogManager().getLogger("")
        }

        @JvmStatic
        fun install() {
            getRootLogger().addHandler(JulLogger())
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

    private val Level.logLevel: LogLevel
        get() = when (this) {
            Level.OFF -> LogLevel.NONE
            Level.SEVERE -> LogLevel.ERROR
            Level.WARNING -> LogLevel.WARNING
            Level.INFO -> LogLevel.INFO
            Level.CONFIG, Level.FINE -> LogLevel.DEBUG
            Level.ALL, Level.FINER, Level.FINEST -> LogLevel.TRACE
            else -> LogLevel.INFO
        }

    override fun publish(record: LogRecord?) {
        if (record == null) return

        val level = record.level?.logLevel ?: LogLevel.INFO

        val logger = KLogger.named(record.loggerName ?: KLogger.defaultLoggerName)
        logger.log(
            LogRecordData(
                loggerName = logger.name,
                level = level,
                body = record.message.orEmpty(),
                exception = record.thrown,
                meta = record.parameters?.withIndex()?.associate { it.index.toString() to it.value } ?: emptyMap(),
                context = LogContext(
                    mapOf(
                        "threadId" to record.threadID,
                        "sourceClassName" to record.sourceClassName,
                        "sourceMethodName" to record.sourceMethodName,
                        "sequenceNumber" to record.sequenceNumber,
                    ),
                ),
                threadName = record.threadName(),
                timestamp = Instant.fromEpochMilliseconds(record.millis),
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun LogRecord.threadName(): String? {
        val current = Thread.currentThread()

        return if (current.id == threadID.toLong()) current.name else "Thread#$threadID"
    }

    override fun flush() {
        //do nothing
    }

    override fun close() {
        //do nothing
    }
}