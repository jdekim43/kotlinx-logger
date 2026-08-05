package kim.jade.log.integration.opentelemetry

import io.opentelemetry.api.logs.Logger

class OTelKotlinxLogger(val scopeName: String) : Logger {

    val kotlinxLogger by kim.jade.log.Logger.lazy(scopeName)

    override fun logRecordBuilder(): OTelKotlinxLogRecordBuilder = OTelKotlinxLogRecordBuilder(this)
}