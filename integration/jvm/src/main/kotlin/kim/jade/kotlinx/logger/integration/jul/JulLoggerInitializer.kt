package kim.jade.kotlinx.logger.integration.jul

import kim.jade.kotlinx.logger.LoggerInitializerProvider

class JulLoggerInitializer : LoggerInitializerProvider {
    override fun run() {
        JulLogger.install()
    }
}
