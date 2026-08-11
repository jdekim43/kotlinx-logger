package kim.jade.kotlinx.logger

import kim.jade.kotlinx.logger.integration.android.AndroidLogSink
import kim.jade.kotlinx.logger.pipeline.LoggerNameShortener

internal actual fun initPlatformLogger() {
    Logger.defaultPipeline[LoggerNameShortener].forEach { it.useSimpleName = true }
    Logger.defaultPipeline.install(AndroidLogSink())
}
