package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.extension.ColoredConsole.Style
import kim.jade.kotlinx.extension.colored
import kim.jade.kotlinx.extension.style
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.SerializedLog

class TextFormatter(
    var printMeta: Boolean = true,
    var enableColor: Boolean = false,
) : LogPipe {

    companion object Key : LogPipe.Key<TextFormatter>

    override val key: LogPipe.Key<out LogPipe> = Key

    private val LogLevel.lineStyle: Style
        get() = when (this) {
            LogLevel.NONE -> Style.NotApplied
            LogLevel.FATAL -> style { red }
            LogLevel.ERROR -> style { red }
            LogLevel.WARNING -> style { yellow }
            LogLevel.INFO -> Style.NotApplied
            LogLevel.DEBUG -> style { black.bright }
            LogLevel.TRACE -> style { black.bright }
        }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        next(format(record))
    }

    fun format(record: LogRecord): SerializedLog.String {
        val text = colored(enableColor) {
            record.timestamp.toString().padEnd(23).black.bright + ' ' + buildString {
                if (record.threadName != null) {
                    append('[')
                    append(record.threadName)
                    append(']')
                    append(' ')
                }
                append(record.level.logName.padEnd(5))
                append(' ')
                append(record.loggerName.padEnd(36))
                append(" - ")
                if (record.eventName != null) {
                    append('#')
                    append(record.eventName)
                    append(' ')
                }
                append(record.body)

                if (printMeta && record.meta.isNotEmpty()) {
                    append(' ')
                    record.meta.map { "${it.key}=${it.value}" }.joinTo(this, ", ", prefix = "(", postfix = ")")
                }
            }.style(record.level.lineStyle) { enableColor }
        }

        return SerializedLog.String(record, text)
    }
}
