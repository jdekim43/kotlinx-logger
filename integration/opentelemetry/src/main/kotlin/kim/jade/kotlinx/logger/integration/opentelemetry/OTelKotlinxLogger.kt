package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.logs.Logger

class OTelKotlinxLogger(
    val scopeName: String,
    val schemaUrl: String? = null,
    val scopeVersion: String? = null,
) : Logger {

    val kotlinxLogger by kim.jade.kotlinx.logger.Logger.lazy(scopeName)

    internal val scopeMeta: Map<String, Any?>? =
        if (schemaUrl == null && scopeVersion == null) null else mapOf(
            "schemaUrl" to schemaUrl,
            "scopeVersion" to scopeVersion,
        )

    override fun logRecordBuilder(): OTelKotlinxLogRecordBuilder = OTelKotlinxLogRecordBuilder(this)
}
