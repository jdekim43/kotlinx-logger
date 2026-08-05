@file:Suppress("unused")

package kim.jade.log.integration.koin

import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE
import kim.jade.log.Logger as KLogger

class KoinLogger(private val logger: KLogger = KLogger.named("KoinApplication")) : Logger(Level.DEBUG) {

    override fun display(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> logger.debug(msg)
            Level.INFO -> logger.info(msg)
            Level.WARNING -> logger.warning(msg)
            Level.ERROR -> logger.error(msg)
            Level.NONE -> {
                //do nothing
            }
        }
    }
}