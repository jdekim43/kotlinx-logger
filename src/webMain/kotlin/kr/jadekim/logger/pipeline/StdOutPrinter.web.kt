package kr.jadekim.logger.pipeline

internal actual fun eprintln(text: String) {
    console.error(text)
}