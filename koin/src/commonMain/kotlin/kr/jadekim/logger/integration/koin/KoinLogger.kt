package kr.jadekim.logger.integration.koin

import kr.jadekim.logger.JLog
import kr.jadekim.logger.JLogger
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

class KoinLogger(private val logger: JLogger = JLog.get("Koin")) : Logger(Level.DEBUG) {

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