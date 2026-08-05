@file:Suppress("unused")

package kim.jade.log.integration.okhttp

import kim.jade.log.LogLevel
import kim.jade.log.Logger
import okhttp3.logging.HttpLoggingInterceptor

object OkHttpLogInterceptorFactory {

    class OkHttpLoggerImpl(
        var logLevel: LogLevel,
        private val logger: Logger,
    ) : HttpLoggingInterceptor.Logger {

        override fun log(message: String) {
            when (logLevel) {
                LogLevel.FATAL -> logger.fatal(message)
                LogLevel.ERROR -> logger.error(message)
                LogLevel.WARNING -> logger.warning(message)
                LogLevel.INFO -> logger.info(message)
                LogLevel.DEBUG -> logger.debug(message)
                LogLevel.TRACE -> logger.trace(message)
                LogLevel.NONE -> {
                    //do nothing
                }
            }
        }
    }

    fun create(
        clientName: String,
        logLevel: LogLevel,
        interceptLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BODY,
        logger: Logger = Logger.named("HttpClientLogger-$clientName"),
    ) = HttpLoggingInterceptor(OkHttpLoggerImpl(logLevel, logger)).apply {
        level = interceptLevel
    }
}