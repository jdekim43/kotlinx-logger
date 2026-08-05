package kim.jade.log.integration.opentelemetry

import io.opentelemetry.api.logs.LoggerProvider

class OTelKotlinxLoggerProvider : LoggerProvider {
    override fun loggerBuilder(instrumentationScopeName: String): OtelKotlinxLoggerBuilder =
        OtelKotlinxLoggerBuilder(instrumentationScopeName)
}