@file:Suppress("unused")

package kim.jade.kotlinx.logger.integration.koin

import kim.jade.kotlinx.logger.LogLevel
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import kim.jade.kotlinx.logger.Logger as KLogger

class KoinLogger(private val logger: KLogger = KLogger.named("KoinApplication")) : Logger(Level.DEBUG) {

    override fun display(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> logger.log(LogLevel.DEBUG, msg)
            Level.INFO -> logger.log(LogLevel.INFO, msg)
            Level.WARNING -> logger.log(LogLevel.WARNING, msg)
            Level.ERROR -> logger.log(LogLevel.ERROR, msg)
            Level.NONE -> logger.log(LogLevel.NONE, msg)
        }
    }
}