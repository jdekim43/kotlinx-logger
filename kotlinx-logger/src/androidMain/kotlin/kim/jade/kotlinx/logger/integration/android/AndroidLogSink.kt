package kim.jade.kotlinx.logger.integration.android

import android.util.Log
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.SerializedLog
import kim.jade.kotlinx.logger.pipeline.LogPipe
import kim.jade.kotlinx.logger.pipeline.StdOutSink

class AndroidLogSink(
    printStackTrace: Boolean = true,
) : StdOutSink(printStackTrace, useStdErr = false) {

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        val message = if (record.eventName == null) record.body else {
            "${record.eventName}: ${record.body}"
        }

        Log.println(record.level.toAndroidLogLevel(), record.loggerName, message)

        if (printStackTrace) {
            record.exception?.printStackTrace()
        }

        next(record)
    }

    private fun LogLevel.toAndroidLogLevel(): Int = when (this) {
        LogLevel.NONE -> Log.VERBOSE
        LogLevel.TRACE -> Log.VERBOSE
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARNING -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
        LogLevel.FATAL -> Log.ASSERT
    }
}
