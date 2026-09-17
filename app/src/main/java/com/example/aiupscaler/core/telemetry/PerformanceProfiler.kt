package com.example.aiupscaler.core.telemetry

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object PerformanceProfiler {

    private val timings = ConcurrentHashMap<String, MutableList<Long>>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun <T> measure(label: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            timings.getOrPut(label) { mutableListOf() }.add(durationMs)
            Telemetry.metric("Profiler", label, durationMs)
        }
    }

    fun count(key: String, delta: Long = 1) {
        counters.getOrPut(key) { AtomicLong(0) }.addAndGet(delta)
    }

    fun snapshot(): Map<String, TimingStats> = timings.mapValues { (_, values) ->
        TimingStats(
            count = values.size,
            total = values.sum(),
            avg = values.average().toLong(),
            min = values.minOrNull() ?: 0,
            max = values.maxOrNull() ?: 0
        )
    }

    fun getCount(key: String): Long = counters[key]?.get() ?: 0

    fun reset() {
        timings.clear()
        counters.clear()
    }

    data class TimingStats(
        val count: Int,
        val total: Long,
        val avg: Long,
        val min: Long,
        val max: Long
    )
}
