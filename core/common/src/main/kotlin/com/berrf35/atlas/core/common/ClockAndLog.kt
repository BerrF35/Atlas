package com.berrf35.atlas.core.common

interface Clock {
    fun nowUtcMs(): Long
}

class SystemClock : Clock {
    override fun nowUtcMs(): Long = System.currentTimeMillis()
}

class TestClock(private var currentMs: Long = 0L) : Clock {
    override fun nowUtcMs(): Long = currentMs
    fun advance(ms: Long) { currentMs += ms }
    fun set(ms: Long) { currentMs = ms }
}

object AtlasLog {
    fun d(tag: String, message: String) {
        println("DEBUG: [$tag] $message")
    }

    fun i(tag: String, message: String) {
        println("INFO:  [$tag] $message")
    }

    fun w(tag: String, message: String) {
        println("WARN:  [$tag] $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        println("ERROR: [$tag] $message ${throwable?.message ?: ""}")
    }
}
