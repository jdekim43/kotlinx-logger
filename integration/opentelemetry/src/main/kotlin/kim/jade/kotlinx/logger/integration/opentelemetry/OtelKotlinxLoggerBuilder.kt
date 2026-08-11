package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.logs.LoggerBuilder

class OtelKotlinxLoggerBuilder(val scopeName: String) : LoggerBuilder {

    private var schemaUrl: String? = null
    private var scopeVersion: String? = null

    override fun setSchemaUrl(schemaUrl: String): OtelKotlinxLoggerBuilder {
        this.schemaUrl = schemaUrl

        return this
    }

    override fun setInstrumentationVersion(instrumentationScopeVersion: String): OtelKotlinxLoggerBuilder {
        this.scopeVersion = instrumentationScopeVersion

        return this
    }

    override fun build(): OTelKotlinxLogger = OTelKotlinxLogger(scopeName, schemaUrl, scopeVersion)
}
