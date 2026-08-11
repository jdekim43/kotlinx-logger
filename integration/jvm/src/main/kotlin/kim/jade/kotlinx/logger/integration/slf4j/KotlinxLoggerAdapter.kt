package kim.jade.kotlinx.logger.integration.slf4j

import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NormalizedParameters
import org.slf4j.spi.LocationAwareLogger

private val CALLER_NAME = KotlinxLoggerAdapter::class.java.name

class KotlinxLoggerAdapter(@Transient private val logger: Logger) : LegacyAbstractLogger(), LocationAwareLogger {

    constructor(name: String) : this(Logger.named(name))

    override fun isTraceEnabled(): Boolean = LogLevel.TRACE.isPrintableAt(logger.level)

    override fun isDebugEnabled(): Boolean = LogLevel.DEBUG.isPrintableAt(logger.level)

    override fun isInfoEnabled(): Boolean = LogLevel.INFO.isPrintableAt(logger.level)

    override fun isWarnEnabled(): Boolean = LogLevel.WARNING.isPrintableAt(logger.level)

    override fun isErrorEnabled(): Boolean = LogLevel.ERROR.isPrintableAt(logger.level)

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        val formattedMessage = MessageFormatter.basicArrayFormat(messagePattern, arguments)
        logger.log(level.toKotlinxLogLevel(), formattedMessage, throwable)
    }

    override fun getFullyQualifiedCallerName(): String = CALLER_NAME

    override fun log(
        marker: Marker?,
        fqcn: String?,
        level: Int,
        message: String?,
        argArray: Array<out Any>?,
        t: Throwable?,
    ) {
        val kotlinxLogLevel = Level.intToLevel(level).toKotlinxLogLevel()

        if (kotlinxLogLevel.isPrintableAt(logger.level)) {
            val normalizedParameters = NormalizedParameters.normalize(message, argArray, t)
            val formattedMessage =
                MessageFormatter.basicArrayFormat(normalizedParameters.message, normalizedParameters.arguments)

            logger.log(kotlinxLogLevel, formattedMessage, normalizedParameters.throwable)
        }
    }

    private fun Level.toKotlinxLogLevel(): LogLevel = when (this) {
        Level.ERROR -> LogLevel.ERROR
        Level.WARN -> LogLevel.WARNING
        Level.INFO -> LogLevel.INFO
        Level.DEBUG -> LogLevel.DEBUG
        Level.TRACE -> LogLevel.TRACE
    }
}