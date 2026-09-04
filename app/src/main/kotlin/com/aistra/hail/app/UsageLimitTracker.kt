package com.aistra.hail.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.core.content.getSystemService
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.utils.HSystem
import java.util.Calendar

object UsageLimitTracker {
    data class Snapshot(
        val dayStartMs: Long,
        val perAppMs: Map<String, Long>,
        val totalMs: Long,
        val foregroundPackage: String?
    )

    private val usageStatsManager by lazy { app.getSystemService<UsageStatsManager>()!! }
    private var state: UsageLimitData.TrackerState? = null

    @Synchronized
    fun snapshot(now: Long = System.currentTimeMillis()): Snapshot {
        val trackedPackages = UsageLimitData.appLimits().keys
        val dayStart = startOfDay(now)
        val signature = UsageLimitData.packageSignature()
        if (trackedPackages.isEmpty()) {
            state = UsageLimitData.TrackerState(
                dayStartMs = dayStart,
                lastProcessedMs = now,
                packageSignature = signature
            ).also(UsageLimitData::saveTrackerState)
            return Snapshot(dayStart, emptyMap(), 0L, null)
        }

        var current = state ?: UsageLimitData.loadTrackerState().also { state = it }
        if (
            current.dayStartMs != dayStart ||
            current.packageSignature != signature ||
            current.lastProcessedMs <= 0L ||
            current.lastProcessedMs > now
        ) {
            current = rebuild(dayStart, now, trackedPackages, signature)
            state = current
        } else if (current.lastProcessedMs < now) {
            processEvents(current, current.lastProcessedMs + 1L, now, trackedPackages)
            current.lastProcessedMs = now
            // If the screen turned off but an OEM omitted the corresponding usage event,
            // stop the active session here. The maximum accounting error is one poll interval.
            if (!HSystem.isInteractive(app) && current.activePackage != null) {
                closeActive(current, now)
            }
            UsageLimitData.saveTrackerState(current)
        }

        val perApp = current.usageMs.toMutableMap()
        current.activePackage?.takeIf { it in trackedPackages }?.let { packageName ->
            val start = maxOf(current.activeSinceMs, current.dayStartMs)
            if (now > start && HSystem.isInteractive(app)) {
                perApp[packageName] = (perApp[packageName] ?: 0L) + (now - start)
            }
        }
        trackedPackages.forEach { perApp.putIfAbsent(it, 0L) }
        return Snapshot(
            dayStartMs = dayStart,
            perAppMs = perApp,
            totalMs = perApp.values.sum(),
            foregroundPackage = current.activePackage
        )
    }

    @Synchronized
    fun invalidate() {
        state = null
        UsageLimitData.clearTrackerState()
    }

    private fun rebuild(
        dayStart: Long,
        now: Long,
        trackedPackages: Set<String>,
        signature: String
    ): UsageLimitData.TrackerState {
        val fresh = UsageLimitData.TrackerState(
            dayStartMs = dayStart,
            lastProcessedMs = now,
            packageSignature = signature
        )
        // Read some history before midnight so an app already in the foreground across
        // the day boundary is accounted from midnight instead of from its next transition.
        val historyStart = (dayStart - 24L * 60L * 60L * 1000L).coerceAtLeast(0L)
        processEvents(fresh, historyStart, now, trackedPackages)
        fresh.lastProcessedMs = now
        UsageLimitData.saveTrackerState(fresh)
        return fresh
    }

    private fun processEvents(
        state: UsageLimitData.TrackerState,
        begin: Long,
        end: Long,
        trackedPackages: Set<String>
    ) {
        if (begin >= end) return
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(begin, end)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    closeActive(state, timestamp)
                    val packageName = event.packageName
                    if (packageName != null && packageName in trackedPackages) {
                        state.activePackage = packageName
                        state.activeSinceMs = timestamp
                    }
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (event.packageName == state.activePackage) closeActive(state, timestamp)
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> closeActive(state, timestamp)
            }
        }
    }

    private fun closeActive(state: UsageLimitData.TrackerState, timestamp: Long) {
        val packageName = state.activePackage ?: return
        val start = maxOf(state.activeSinceMs, state.dayStartMs)
        if (timestamp > start) {
            state.usageMs[packageName] = (state.usageMs[packageName] ?: 0L) + (timestamp - start)
        }
        state.activePackage = null
        state.activeSinceMs = 0L
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    fun hasUsageAccess(context: Context = app): Boolean = HSystem.checkOpUsageStats(context)
}
