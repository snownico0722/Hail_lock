package com.aistra.hail.app

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aistra.hail.R
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HPolicy
import java.util.Calendar
import kotlin.math.min

object UsageLimitController {
    private const val CHANNEL_ID = "usage_limits"
    private const val FIVE_MINUTES_MS = 5L * 60L * 1000L
    private const val ONE_MINUTE_MS = 60L * 1000L

    fun tick(context: Context) {
        if (!HPolicy.isDeviceOwnerActive) return

        val dayStart = startOfDay(System.currentTimeMillis())
        resetForNewDay(dayStart)
        if (!UsageLimitData.enabled || !UsageLimitTracker.hasUsageAccess(context)) return

        val limits = UsageLimitData.appLimits()
        if (limits.isEmpty()) return
        val snapshot = UsageLimitTracker.snapshot()

        val totalLimitMs = UsageLimitData.totalLimitMinutes.takeIf { it > 0 }?.times(60_000L)
        if (totalLimitMs != null && snapshot.totalMs >= totalLimitMs) {
            notifyLimitReached(context, null, true)
            limits.keys.forEach(::enforcePackage)
            return
        }

        limits.forEach { (packageName, limitMinutes) ->
            val usedMs = snapshot.perAppMs[packageName] ?: 0L
            if (usedMs >= limitMinutes * 60_000L) {
                notifyLimitReached(context, packageName, false)
                enforcePackage(packageName)
            }
        }

        maybeWarnForeground(context, snapshot, limits, totalLimitMs)
    }

    fun releaseAllEnforced() {
        if (!HPolicy.isDeviceOwnerActive) return
        UsageLimitData.enforcedPackages().forEach { packageName ->
            runCatching { HPolicy.setAppSuspended(packageName, false) }
        }
        UsageLimitData.clearEnforcedPackages()
    }

    fun removePackage(packageName: String) {
        UsageLimitData.removeAppLimit(packageName)
        UsageLimitTracker.invalidate()
        if (packageName in UsageLimitData.enforcedPackages() && HPolicy.isDeviceOwnerActive) {
            runCatching { HPolicy.setAppSuspended(packageName, false) }
            UsageLimitData.unmarkEnforced(packageName)
        }
    }

    private fun resetForNewDay(dayStartMs: Long) {
        val day = dayStartMs.toString()
        if (UsageLimitData.enforcementDay == day) return
        releaseAllEnforced()
        UsageLimitData.enforcementDay = day
        UsageLimitData.resetWarningsForDay(day)
        UsageLimitTracker.invalidate()
    }

    private fun enforcePackage(packageName: String) {
        if (packageName == com.aistra.hail.BuildConfig.APPLICATION_ID) return
        if (packageName in UsageLimitData.enforcedPackages()) return
        // Do not claim a suspension that existed before the usage limiter; otherwise
        // the next daily reset could accidentally undo a manual freeze.
        if (HPackages.isAppSuspended(packageName)) return
        if (runCatching { HPolicy.setAppSuspended(packageName, true) }.getOrDefault(false)) {
            UsageLimitData.markEnforced(packageName)
        }
    }

    private fun maybeWarnForeground(
        context: Context,
        snapshot: UsageLimitTracker.Snapshot,
        limits: Map<String, Int>,
        totalLimitMs: Long?
    ) {
        val packageName = snapshot.foregroundPackage ?: return
        val appLimitMinutes = limits[packageName] ?: return
        val appRemaining = (appLimitMinutes * 60_000L - (snapshot.perAppMs[packageName] ?: 0L)).coerceAtLeast(0L)
        val totalRemaining = totalLimitMs?.let { (it - snapshot.totalMs).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        val remaining = min(appRemaining, totalRemaining)
        if (remaining <= 0L) return
        val totalIsBinding = totalRemaining < appRemaining

        when {
            remaining <= ONE_MINUTE_MS -> warnOnce(context, packageName, totalIsBinding, 1)
            remaining <= FIVE_MINUTES_MS -> warnOnce(context, packageName, totalIsBinding, 5)
        }
    }

    private fun warnOnce(context: Context, packageName: String, total: Boolean, minutes: Int) {
        val scope = if (total) "total" else packageName
        val marker = "$scope:$minutes"
        if (!UsageLimitData.markWarningOnce(marker)) return
        ensureChannel(context)
        val appName = appName(context, packageName)
        val text = if (total) {
            context.getString(R.string.usage_limit_warning_total, minutes)
        } else {
            context.getString(R.string.usage_limit_warning_app, appName, minutes)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_outline_lock)
            .setContentTitle(context.getString(R.string.title_usage_limits))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(("warning:$marker").hashCode(), notification)
        }
    }

    private fun notifyLimitReached(context: Context, packageName: String?, total: Boolean) {
        val marker = if (total) "reached:total" else "reached:$packageName"
        if (!UsageLimitData.markWarningOnce(marker)) return
        ensureChannel(context)
        val text = if (total) {
            context.getString(R.string.usage_limit_reached_total)
        } else {
            context.getString(R.string.usage_limit_reached_app, appName(context, packageName!!))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_outline_lock)
            .setContentTitle(context.getString(R.string.title_usage_limits))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(marker.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH
        ).setName(context.getString(R.string.title_usage_limits)).build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun appName(context: Context, packageName: String): String =
        HPackages.getApplicationInfoOrNull(packageName)?.loadLabel(context.packageManager)?.toString() ?: packageName

    private fun startOfDay(now: Long): Long = Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}
