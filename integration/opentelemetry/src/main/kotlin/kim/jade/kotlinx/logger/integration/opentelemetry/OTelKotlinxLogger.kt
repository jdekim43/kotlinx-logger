package kim.jade.kotlinx.logger.integration.opentelemetry

import io.opentelemetry.api.logs.Logger

class OTelKotlinxLogger(val scopeName: String) : Logger {

    val kotlinxLogger by kim.jade.kotlinx.logger.Logger.lazy(scopeName)

    override fun logRecordBuilder(): OTelKotlinxLogRecordBuilder = OTelKotlinxLogRecordBuilder(this)
}