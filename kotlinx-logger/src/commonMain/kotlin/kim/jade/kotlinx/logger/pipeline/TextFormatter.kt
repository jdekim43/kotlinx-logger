package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.extension.ColoredConsole.Style
import kim.jade.kotlinx.extension.colored
import kim.jade.kotlinx.extension.style
import kim.jade.kotlinx.logger.LogLevel
import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.SerializedLog
import kim.jade.kotlinx.logger.util.escapedForLog

class TextFormatter(
    var printMeta: Boolean = true,
    var enableColor: Boolean = false,
    var escapeControlChars: Boolean = true,
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
        val threadName = record.threadName
        val eventName = record.eventName

        val text = colored(enableColor) {
            record.timestamp.toString().padEnd(23).black.bright + ' ' + buildString {
                if (threadName != null) {
                    append('[')
                    append(threadName.escaped())
                    append(']')
                    append(' ')
                }
                append(record.level.logName.padEnd(5))
                append(' ')
                append(record.loggerName.escaped().padEnd(36))
                append(" - ")
                if (eventName != null) {
                    append('#')
                    append(eventName.escaped())
                    append(' ')
                }
                append(record.body.escaped())

                if (printMeta && record.meta.isNotEmpty()) {
                    append(' ')
                    record.meta
                        .map { "${it.key.escaped()}=${it.value.toString().escaped()}" }
                        .joinTo(this, ", ", prefix = "(", postfix = ")")
                }
            }.style(record.level.lineStyle) { enableColor }
        }

        return SerializedLog.String(record, text)
    }

    private fun String.escaped(): String = if (escapeControlChars) escapedForLog() else this
}
