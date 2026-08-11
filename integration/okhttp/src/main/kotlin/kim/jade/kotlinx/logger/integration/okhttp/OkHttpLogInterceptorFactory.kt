@file:Suppress("unused")

package kim.jade.kotlinx.logger.integration.okhttp

import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.Logger
import okhttp3.logging.HttpLoggingInterceptor

object OkHttpLogInterceptorFactory {

    class OkHttpLoggerImpl(
        var logLevel: LogLevel,
        private val logger: Logger,
    ) : HttpLoggingInterceptor.Logger {

        override fun log(message: String) {
            logger.log(logLevel, message)
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