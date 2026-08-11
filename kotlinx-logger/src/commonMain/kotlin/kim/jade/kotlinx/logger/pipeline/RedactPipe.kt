@file:Suppress("unused")

package kim.jade.kotlinx.logger.pipeline

import kim.jade.kotlinx.logger.LogRecord
import kim.jade.kotlinx.logger.LogRecordData
import kim.jade.kotlinx.logger.context.LogContext

class RedactPipe(
    keys: Set<String> = DEFAULT_KEYS,
    var keyPatterns: List<Regex> = DEFAULT_KEY_PATTERNS,
    var placeholder: String = "***",
    var maxDepth: Int = 8,
) : LogPipe {

    var keys: Set<String> = keys.normalizeKeys()
        set(value) {
            field = value.normalizeKeys()
        }

    companion object Key : LogPipe.Key<RedactPipe> {

        val DEFAULT_KEYS: Set<String> = setOf(
            "authorization",
            "proxyauthorization",
            "wwwauthenticate",
            "cookie",
            "cookies",
            "setcookie",
            "token",
            "accesstoken",
            "refreshtoken",
            "idtoken",
            "bearertoken",
            "sessiontoken",
            "session",
            "sessionid",
            "jsessionid",
            "csrf",
            "csrftoken",
            "xsrftoken",
            "xcsrftoken",
            "xxsrftoken",
            "xauthtoken",
            "xapikey",
            "apikey",
            "apisecret",
            "appsecret",
            "clientsecret",
            "secret",
            "password",
            "passwd",
            "pwd",
            "passphrase",
            "credential",
            "credentials",
            "privatekey",
            "signature",
            "otp",
            "pin",
            "cvv",
            "cvc",
            "cardnumber",
            "creditcard",
            "ssn",
        )

        val DEFAULT_KEY_PATTERNS: List<Regex> = listOf(
            Regex("password"),
            Regex("passwd"),
            Regex("passphrase"),
            Regex("secret"),
            Regex("credential"),
            Regex("privatekey"),
            Regex("apikey"),
            Regex("authorization"),
        )

        internal fun Set<String>.normalizeKeys(): Set<String> = mapTo(mutableSetOf()) { it.normalizeKey() }

        internal fun String.normalizeKey(): String {
            val alreadyNormalized = all { it.isDigit() || (it.isLetter() && it.isLowerCase()) }
            if (alreadyNormalized) {
                return this
            }

            return buildString(length) {
                for (character in this@normalizeKey) {
                    if (character.isLetterOrDigit()) {
                        append(character.lowercaseChar())
                    }
                }
            }
        }
    }

    override val key: LogPipe.Key<out LogPipe> = Key

    override fun addTo(pipeline: LogPipeline, index: Int) {
        if (pipeline.isInstalled(Key)) return

        super.addTo(pipeline, index)
    }

    override fun apply(record: LogRecord, next: (LogRecord) -> Unit) {
        next(redact(record))
    }

    fun redact(record: LogRecord): LogRecord {
        if (record !is LogRecordData) {
            return record
        }

        val meta = record.meta.redactedMap(0)
        val context = record.context.redactedMap(0)

        if (meta === record.meta && context === record.context) {
            return record
        }

        return record.copy(meta = meta, context = LogContext(context))
    }

    fun isSensitive(key: String): Boolean {
        val normalized = key.normalizeKey()

        return normalized in keys || keyPatterns.any { it.containsMatchIn(normalized) }
    }

    private fun Map<String, Any?>.redactedMap(depth: Int): Map<String, Any?> {
        if (isEmpty() || depth > maxDepth) {
            return this
        }

        var changed = false
        val redacted = LinkedHashMap<String, Any?>(size)

        for ((key, value) in this) {
            val replacement = if (isSensitive(key)) placeholder else value.redactedValue(depth)

            if (replacement !== value) {
                changed = true
            }

            redacted[key] = replacement
        }

        return if (changed) redacted else this
    }

    private fun Any?.redactedValue(depth: Int): Any? {
        if (depth > maxDepth) {
            return this
        }

        return when (this) {
            is Map<*, *> -> redactedNestedMap(depth)
            is List<*> -> redactedElements(depth) ?: this
            is Set<*> -> redactedElements(depth)?.toSet() ?: this
            else -> this
        }
    }

    private fun Map<*, *>.redactedNestedMap(depth: Int): Any {
        val stringKeyed = LinkedHashMap<String, Any?>(size)
        for ((key, value) in this) {
            stringKeyed[key?.toString() ?: "null"] = value
        }

        val redacted = stringKeyed.redactedMap(depth + 1)

        return if (redacted === stringKeyed) this else redacted
    }

    private fun Collection<Any?>.redactedElements(depth: Int): List<Any?>? {
        var changed = false
        val mapped = map { element ->
            val replacement = element.redactedValue(depth + 1)
            if (replacement !== element) {
                changed = true
            }

            replacement
        }

        return if (changed) mapped else null
    }
}
