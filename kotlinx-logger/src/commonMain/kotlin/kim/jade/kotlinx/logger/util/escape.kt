@file:Suppress("unused")

package kim.jade.kotlinx.logger.util

private const val ESCAPE: Char = '\u001B'
private const val DELETE: Char = '\u007F'
private const val C1_START: Char = '\u0080'
private const val C1_END: Char = '\u009F'

private const val ESCAPE_HEADROOM: Int = 16
private const val MAX_ESCAPE_DEPTH: Int = 8

/**
 * Characters that let a logged value break out of the line it belongs to.
 *
 * Line terminators forge new records in line-oriented sinks, and `ESC` starts a terminal control sequence that
 * can repaint or erase what a reader already saw. Both are reachable from user input whenever a request
 * parameter, header, or response body ends up in a record.
 */
private fun Char.isUnsafeInLog(): Boolean = this < ' ' || this == DELETE || this in C1_START..C1_END

/**
 * Escapes every control character so the value cannot forge a log line or emit a terminal escape sequence.
 *
 * Returns the receiver itself when there is nothing to escape, so the common case allocates nothing.
 */
fun String.escapedForLog(): String {
    var index = 0
    while (index < length) {
        if (this[index].isUnsafeInLog()) {
            break
        }
        index++
    }

    if (index == length) {
        return this
    }

    return buildString(length + ESCAPE_HEADROOM) {
        append(this@escapedForLog, 0, index)

        for (position in index until this@escapedForLog.length) {
            when (val character = this@escapedForLog[position]) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                ESCAPE -> append("\\e")
                else -> if (character.isUnsafeInLog()) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}

/** Applies [escapedForLog] to strings nested in maps and collections, leaving other values untouched. */
internal fun Any?.escapedValueForLog(depth: Int = 0): Any? = when {
    depth > MAX_ESCAPE_DEPTH -> this
    this is String -> escapedForLog()
    this is Map<*, *> -> entries.associate {
        it.key.escapedValueForLog(depth + 1) to it.value.escapedValueForLog(depth + 1)
    }

    this is List<*> -> map { it.escapedValueForLog(depth + 1) }
    this is Set<*> -> mapTo(mutableSetOf()) { it.escapedValueForLog(depth + 1) }
    else -> this
}
