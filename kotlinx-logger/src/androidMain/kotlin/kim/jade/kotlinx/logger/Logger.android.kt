package kim.jade.kotlinx.logger

import kim.jade.kotlinx.logger.integration.android.AndroidLogSink

internal actual fun initPlatformLogger() {
    Logger.pipeline.install(AndroidLogSink())
}