package com.example.aiupscaler.core.telemetry

import java.util.concurrent.ConcurrentHashMap

object PerformanceProfiler {

    private val timings = ConcurrentHashMap<String, MutableList<Long>>()

    fun <T> measure(label: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            timings.getOrPut(label) { mutableListOf() }.add(durationMs)
        }
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

    fun reset() = timings.clear()

    data class TimingStats(
        val count: Int, val total: Long, val avg: Long,
        val min: Long, val max: Long
    )
}
