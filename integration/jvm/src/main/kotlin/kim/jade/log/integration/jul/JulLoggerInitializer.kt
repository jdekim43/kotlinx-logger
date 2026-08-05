package kim.jade.log.integration.jul

import kim.jade.log.LoggerInitializerProvider

class JulLoggerInitializer : LoggerInitializerProvider {
    override fun run() {
        JulLogger.install()
    }
}
