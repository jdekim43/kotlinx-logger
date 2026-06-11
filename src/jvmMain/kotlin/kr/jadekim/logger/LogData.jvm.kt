package kr.jadekim.logger

actual fun getThreadName(): String? = Thread.currentThread().name