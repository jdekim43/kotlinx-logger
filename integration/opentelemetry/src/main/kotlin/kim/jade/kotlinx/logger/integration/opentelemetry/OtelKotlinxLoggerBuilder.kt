package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.logs.LoggerBuilder
import kim.jade.kotlinx.logger.context.ThreadLogContext

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

    override fun build(): OTelKotlinxLogger {
        ThreadLogContext["otel"] = mapOf(
            "schemaUrl" to schemaUrl,
            "scopeVersion" to scopeVersion
        )

        return OTelKotlinxLogger(scopeName)
    }
}