package com.example.aiupscaler.core.telemetry

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central telemetry & logging.
 * Semua log, metrik, dan error dikumpulkan di sini.
 */
object Telemetry {

    private const val TAG = "AIUpscaler"
    private const val MAX_EVENTS = 200

    data class Event(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val data: Map<String, Any> = emptyMap()
    )

    enum class Level { DEBUG, INFO, WARN, ERROR, METRIC }

    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()
    private val events = ArrayDeque<Event>()

    @Synchronized
    fun log(level: Level, tag: String, message: String, data: Map<String, Any> = emptyMap()) {
        val event = Event(System.currentTimeMillis(), level, tag, message, data)
        events.addLast(event)
        if (events.size > MAX_EVENTS) events.removeFirst()

        when (level) {
            Level.ERROR -> Log.e(TAG, "[$tag] $message")
            Level.WARN -> Log.w(TAG, "[$tag] $message")
            Level.INFO -> Log.i(TAG, "[$tag] $message")
            else -> Log.d(TAG, "[$tag] $message")
        }

        listeners.forEach { it(event) }
    }

    fun debug(tag: String, msg: String, data: Map<String, Any> = emptyMap()) =
        log(Level.DEBUG, tag, msg, data)

    fun info(tag: String, msg: String, data: Map<String, Any> = emptyMap()) =
        log(Level.INFO, tag, msg, data)

    fun warn(tag: String, msg: String, data: Map<String, Any> = emptyMap()) =
        log(Level.WARN, tag, msg, data)

    fun error(tag: String, msg: String, data: Map<String, Any> = emptyMap()) =
        log(Level.ERROR, tag, msg, data)

    fun metric(tag: String, name: String, value: Number) =
        log(Level.METRIC, tag, name, mapOf("value" to value))

    fun subscribe(listener: (Event) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    fun recent(limit: Int = 50): List<Event> = events.takeLast(limit)

    fun clear() = events.clear()
}
