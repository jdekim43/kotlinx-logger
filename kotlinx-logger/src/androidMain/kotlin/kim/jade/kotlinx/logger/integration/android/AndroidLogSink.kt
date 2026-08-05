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

    override fun apply(record: LogRecord): LogRecord {
        if (record is SerializedLog<*> && record.serialized is String) {
            Log.println(record.level.toAndroidLogLevel(), record.loggerName, record.serialized)
        } else {
            Log.e(
                "AndroidLogSink",
                "ERROR: StdOutSink is only acceptable SerializedLog<String>. Require to install TextFormatter before printer",
            )
        }

        if (printStackTrace) {
            record.exception?.printStackTrace()
        }

        return record
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