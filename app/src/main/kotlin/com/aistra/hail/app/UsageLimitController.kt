package com.aistra.hail.app

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aistra.hail.BuildConfig
import com.aistra.hail.R
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HPolicy
import java.util.Calendar
import kotlin.math.min

object UsageLimitController {
    private const val CHANNEL_ID = "usage_limits"
    private const val FIVE_MINUTES_MS = 5L * 60L * 1000L
    private const val ONE_MINUTE_MS = 60L * 1000L

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @Synchronized
    fun tick(context: Context) {
        if (!isSupported || !HPolicy.isDeviceOwnerActive) return

        val dayStart = startOfDay(System.currentTimeMillis())
        resetForNewDay(dayStart)
        reconcile(context, notify = true)
    }

    /**
     * Reconcile the real package suspension state with the current usage-limit policy.
     * This is the single source of truth for enforcement after timer ticks, setting
     * changes, permission changes, and manual unsuspension attempts.
     */
    @Synchronized
    fun reconcile(context: Context, notify: Boolean = false) {
        if (!isSupported || !HPolicy.isDeviceOwnerActive) return

        val limits = UsageLimitData.appLimits()
        if (!UsageLimitData.enabled || limits.isEmpty()) {
            reconcileSuspensions(emptySet())
            return
        }

        // Fail closed: if usage access is revoked while limits are enabled, keep all
        // limited apps suspended until access is restored and their real usage can be
        // evaluated again.
        if (!UsageLimitTracker.hasUsageAccess(context)) {
            reconcileSuspensions(limits.keys)
            return
        }

        val snapshot = UsageLimitTracker.snapshot()
        val totalLimitMs = UsageLimitData.totalLimitMinutes.takeIf { it > 0 }?.times(60_000L)
        val desiredSuspensions = linkedSetOf<String>()

        val totalReached = totalLimitMs != null && snapshot.totalMs >= totalLimitMs
        if (totalReached) {
            desiredSuspensions.addAll(limits.keys)
            if (notify) notifyLimitReached(context, null, true)
        } else {
            limits.forEach { (packageName, limitMinutes) ->
                val usedMs = snapshot.perAppMs[packageName] ?: 0L
                if (usedMs >= limitMinutes * 60_000L) {
                    desiredSuspensions += packageName
                    if (notify) notifyLimitReached(context, packageName, false)
                }
            }
        }

        reconcileSuspensions(desiredSuspensions)

        if (notify && !totalReached) {
            maybeWarnForeground(context, snapshot, limits, totalLimitMs)
        }
    }

    @Synchronized
    fun releaseAllEnforced() {
        if (!HPolicy.isDeviceOwnerActive) return
        UsageLimitData.enforcedPackages().toList().forEach(::releaseOwnedPackage)
    }

    @Synchronized
    fun removePackage(packageName: String) {
        UsageLimitData.removeAppLimit(packageName)
        UsageLimitTracker.invalidate()
        // Removing an app from the limiter is an explicit request to stop limiter
        // enforcement for that app immediately. Other packages are reconciled by the
        // regular service tick.
        releaseOwnedPackage(packageName)
    }

    /**
     * An ordinary Hail freeze request in a suspend-based working mode takes ownership
     * away from the daily limiter. This prevents the next daily reset from undoing a
     * later manual/automatic freeze that happened while the app was already suspended.
     */
    @Synchronized
    fun promoteExternalFreeze(packageNames: Iterable<String>) {
        if (!HailData.workingMode.endsWith(HailData.SUSPEND)) return
        val owned = UsageLimitData.enforcedPackages()
        packageNames.filter { it in owned }.forEach(UsageLimitData::unmarkEnforced)
    }

    @Synchronized
    fun promoteExternalFreeze(packageName: String) = promoteExternalFreeze(listOf(packageName))

    private fun resetForNewDay(dayStartMs: Long) {
        val day = dayStartMs.toString()
        if (UsageLimitData.enforcementDay == day) return
        releaseAllEnforced()
        UsageLimitData.enforcementDay = day
        UsageLimitData.resetWarningsForDay(day)
        UsageLimitTracker.invalidate()
    }

    private fun reconcileSuspensions(desiredSuspensions: Set<String>) {
        val owned = UsageLimitData.enforcedPackages()

        // First release limiter-owned suspensions that are no longer required by the
        // current policy. Never touch suspensions that the limiter does not own.
        (owned - desiredSuspensions).forEach(::releaseOwnedPackage)

        // Then ensure every package that should be blocked is actually suspended.
        // If the user manually unsuspended a limiter-owned package, this re-applies it.
        desiredSuspensions.forEach(::enforcePackage)
    }

    private fun releaseOwnedPackage(packageName: String) {
        if (packageName !in UsageLimitData.enforcedPackages()) return
        if (!HPackages.isAppSuspended(packageName)) {
            UsageLimitData.unmarkEnforced(packageName)
            return
        }
        if (runCatching { HPolicy.setAppSuspended(packageName, false) }.getOrDefault(false)) {
            UsageLimitData.unmarkEnforced(packageName)
        }
    }

    private fun enforcePackage(packageName: String) {
        if (packageName == BuildConfig.APPLICATION_ID) return

        val owned = packageName in UsageLimitData.enforcedPackages()
        val actuallySuspended = HPackages.isAppSuspended(packageName)

        if (owned) {
            if (actuallySuspended) return
            // The limiter owns this suspension, so a manual unsuspend must not turn
            // into a bypass. Re-apply the suspension without changing ownership.
            runCatching { HPolicy.setAppSuspended(packageName, true) }
            return
        }

        // Do not claim a suspension that existed before the usage limiter; otherwise
        // a later reconcile or daily reset could accidentally undo a manual freeze.
        if (actuallySuspended) return
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
