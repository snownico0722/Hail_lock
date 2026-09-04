package com.aistra.hail.app

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.aistra.hail.HailApp.Companion.app
import org.json.JSONObject

object UsageLimitData {
    const val DEFAULT_APP_LIMIT_MINUTES = 30
    const val BACKGROUND_HIDE = "background_hide"

    private const val KEY_ENABLED = "usage_limit_enabled"
    private const val KEY_APP_LIMITS = "usage_limit_app_limits"
    private const val KEY_TOTAL_LIMIT_MINUTES = "usage_limit_total_minutes"
    private const val KEY_ENFORCED_PACKAGES = "usage_limit_enforced_packages"
    private const val KEY_ENFORCEMENT_DAY = "usage_limit_enforcement_day"
    private const val KEY_WARNING_MARKERS = "usage_limit_warning_markers"
    private const val KEY_WARNING_DAY = "usage_limit_warning_day"

    private const val KEY_TRACKER_DAY_START = "usage_limit_tracker_day_start"
    private const val KEY_TRACKER_LAST_PROCESSED = "usage_limit_tracker_last_processed"
    private const val KEY_TRACKER_ACTIVE_PACKAGE = "usage_limit_tracker_active_package"
    private const val KEY_TRACKER_ACTIVE_SINCE = "usage_limit_tracker_active_since"
    private const val KEY_TRACKER_USAGE = "usage_limit_tracker_usage"
    private const val KEY_TRACKER_SIGNATURE = "usage_limit_tracker_signature"

    private val sp = PreferenceManager.getDefaultSharedPreferences(app)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit { putBoolean(KEY_ENABLED, value) }

    val backgroundHide: Boolean
        get() = sp.getBoolean(BACKGROUND_HIDE, false)

    var totalLimitMinutes: Int
        get() = sp.getInt(KEY_TOTAL_LIMIT_MINUTES, 0)
        set(value) = sp.edit { putInt(KEY_TOTAL_LIMIT_MINUTES, value.coerceAtLeast(0)) }

    fun appLimits(): Map<String, Int> {
        val objectValue = runCatching { JSONObject(sp.getString(KEY_APP_LIMITS, "{}") ?: "{}") }
            .getOrElse { JSONObject() }
        val result = linkedMapOf<String, Int>()
        objectValue.keys().asSequence().sorted().forEach { packageName ->
            val minutes = objectValue.optInt(packageName, 0)
            if (minutes > 0) result[packageName] = minutes
        }
        return result
    }

    fun setAppLimit(packageName: String, minutes: Int) {
        require(minutes > 0)
        val objectValue = JSONObject().apply {
            appLimits().forEach { (pkg, limit) -> put(pkg, limit) }
            put(packageName, minutes)
        }
        sp.edit { putString(KEY_APP_LIMITS, objectValue.toString()) }
    }

    fun removeAppLimit(packageName: String) {
        val objectValue = JSONObject().apply {
            appLimits().filterKeys { it != packageName }.forEach { (pkg, limit) -> put(pkg, limit) }
        }
        sp.edit { putString(KEY_APP_LIMITS, objectValue.toString()) }
    }

    fun packageSignature(): String = appLimits().keys.sorted().joinToString("\n")

    var enforcementDay: String?
        get() = sp.getString(KEY_ENFORCEMENT_DAY, null)
        set(value) = sp.edit {
            if (value == null) remove(KEY_ENFORCEMENT_DAY) else putString(KEY_ENFORCEMENT_DAY, value)
        }

    fun enforcedPackages(): Set<String> = sp.getStringSet(KEY_ENFORCED_PACKAGES, emptySet())?.toSet().orEmpty()

    fun markEnforced(packageName: String) {
        val packages = enforcedPackages().toMutableSet().apply { add(packageName) }
        sp.edit { putStringSet(KEY_ENFORCED_PACKAGES, packages) }
    }

    fun unmarkEnforced(packageName: String) {
        val packages = enforcedPackages().toMutableSet().apply { remove(packageName) }
        sp.edit { putStringSet(KEY_ENFORCED_PACKAGES, packages) }
    }

    fun clearEnforcedPackages() = sp.edit { remove(KEY_ENFORCED_PACKAGES) }

    fun resetWarningsForDay(day: String) {
        if (sp.getString(KEY_WARNING_DAY, null) == day) return
        sp.edit {
            putString(KEY_WARNING_DAY, day)
            remove(KEY_WARNING_MARKERS)
        }
    }

    fun markWarningOnce(marker: String): Boolean {
        val markers = sp.getStringSet(KEY_WARNING_MARKERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!markers.add(marker)) return false
        sp.edit { putStringSet(KEY_WARNING_MARKERS, markers) }
        return true
    }

    data class TrackerState(
        var dayStartMs: Long = 0L,
        var lastProcessedMs: Long = 0L,
        var activePackage: String? = null,
        var activeSinceMs: Long = 0L,
        val usageMs: MutableMap<String, Long> = mutableMapOf(),
        var packageSignature: String = ""
    )

    fun loadTrackerState(): TrackerState {
        val usage = mutableMapOf<String, Long>()
        runCatching { JSONObject(sp.getString(KEY_TRACKER_USAGE, "{}") ?: "{}") }.getOrNull()?.let { json ->
            json.keys().forEach { packageName ->
                val value = json.optLong(packageName, 0L)
                if (value > 0L) usage[packageName] = value
            }
        }
        return TrackerState(
            dayStartMs = sp.getLong(KEY_TRACKER_DAY_START, 0L),
            lastProcessedMs = sp.getLong(KEY_TRACKER_LAST_PROCESSED, 0L),
            activePackage = sp.getString(KEY_TRACKER_ACTIVE_PACKAGE, null),
            activeSinceMs = sp.getLong(KEY_TRACKER_ACTIVE_SINCE, 0L),
            usageMs = usage,
            packageSignature = sp.getString(KEY_TRACKER_SIGNATURE, "") ?: ""
        )
    }

    fun saveTrackerState(state: TrackerState) {
        val usage = JSONObject().apply {
            state.usageMs.forEach { (packageName, millis) ->
                if (millis > 0L) put(packageName, millis)
            }
        }
        sp.edit {
            putLong(KEY_TRACKER_DAY_START, state.dayStartMs)
            putLong(KEY_TRACKER_LAST_PROCESSED, state.lastProcessedMs)
            putLong(KEY_TRACKER_ACTIVE_SINCE, state.activeSinceMs)
            putString(KEY_TRACKER_USAGE, usage.toString())
            putString(KEY_TRACKER_SIGNATURE, state.packageSignature)
            if (state.activePackage == null) remove(KEY_TRACKER_ACTIVE_PACKAGE)
            else putString(KEY_TRACKER_ACTIVE_PACKAGE, state.activePackage)
        }
    }

    fun clearTrackerState() = sp.edit {
        remove(KEY_TRACKER_DAY_START)
        remove(KEY_TRACKER_LAST_PROCESSED)
        remove(KEY_TRACKER_ACTIVE_PACKAGE)
        remove(KEY_TRACKER_ACTIVE_SINCE)
        remove(KEY_TRACKER_USAGE)
        remove(KEY_TRACKER_SIGNATURE)
    }
}
