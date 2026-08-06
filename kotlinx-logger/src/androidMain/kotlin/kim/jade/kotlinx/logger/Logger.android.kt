package kim.jade.kotlinx.logger

import kim.jade.kotlinx.logger.integration.android.AndroidLogSink
import kim.jade.kotlinx.logger.pipeline.LoggerNameShortener

internal actual fun initPlatformLogger() {
    Logger.pipeline[LoggerNameShortener].forEach { it.useSimpleName = true }
    Logger.pipeline.install(AndroidLogSink())
}