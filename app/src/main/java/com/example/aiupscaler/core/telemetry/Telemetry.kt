package com.example.aiupscaler.core.telemetry

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

object Telemetry {

    private const val TAG = "AIUpscaler"
    private const val MAX_EVENTS = 200

    data class Event(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()
    private val events = ArrayDeque<Event>()

    @Synchronized
    fun log(level: Level, tag: String, message: String) {
        val event = Event(System.currentTimeMillis(), level, tag, message)
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

    fun debug(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun info(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun warn(tag: String, msg: String) = log(Level.WARN, tag, msg)
    fun error(tag: String, msg: String) = log(Level.ERROR, tag, msg)

    fun subscribe(listener: (Event) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }
}
