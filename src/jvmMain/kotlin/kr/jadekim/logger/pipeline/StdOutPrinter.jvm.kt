package kr.jadekim.logger.pipeline

internal actual fun eprintln(text: String) {
    System.err.println(text)
}
