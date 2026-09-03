package com.aistra.hail.utils

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.UserManager
import androidx.core.content.getSystemService
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.receiver.DeviceAdminReceiver

object HPolicy {
    private val dpm = app.getSystemService<DevicePolicyManager>()!!
    private val admin = ComponentName(app, DeviceAdminReceiver::class.java)
    private val DPM_COMMAND = "dpm set-device-owner ${admin.flattenToShortString()}"
    val ADB_COMMAND = "adb shell $DPM_COMMAND"

    private val isDeviceOwner get() = dpm.isDeviceOwnerApp(app.packageName)
    val isProfileOwner get() = dpm.isProfileOwnerApp(app.packageName)
    val isAdminActive get() = dpm.isAdminActive(admin)
    val isDeviceOwnerActive get() = isDeviceOwner && isAdminActive

    val lockScreen get() = isAdminActive.also { if (it) dpm.lockNow() }

    fun setAppHidden(packageName: String, hidden: Boolean): Boolean =
        isDeviceOwnerActive && dpm.setApplicationHidden(admin, packageName, hidden)

    fun setAppSuspended(packageName: String, suspended: Boolean): Boolean =
        isDeviceOwnerActive && HTarget.N && dpm.setPackagesSuspended(
            admin, arrayOf(packageName), suspended
        ).isEmpty()

    fun uninstallApp(packageName: String): Boolean = when {
        isDeviceOwnerActive -> {
            app.packageManager.packageInstaller.uninstall(
                packageName, PendingIntent.getActivity(
                    app, 0, Intent(), PendingIntent.FLAG_IMMUTABLE
                ).intentSender
            )
            true
        }

        else -> false
    }

    fun enableBackupService() {
        if (isDeviceOwnerActive && HTarget.O && !dpm.isBackupServiceEnabled(admin)) dpm.setBackupServiceEnabled(
            admin, true
        )
    }

    /**
     * Keep the device in its primary user by blocking creation and switching of
     * secondary users. OEM "second space" features are commonly backed by this
     * Android multi-user mechanism.
     *
     * Existing secondary-user data is deliberately not deleted.
     */
    fun enforceSingleUserMode() {
        if (!isDeviceOwnerActive) return
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        if (HTarget.P) dpm.addUserRestriction(admin, UserManager.DISALLOW_USER_SWITCH)
    }

    fun setOrganizationName(name: String? = null) {
        if (isDeviceOwnerActive && HTarget.O) dpm.setOrganizationName(admin, name)
    }

    fun removeActiveAdmin() {
        if (isAdminActive) dpm.removeActiveAdmin(admin)
    }

    @Suppress("DEPRECATION")
    fun removeProfileOwner() {
        if (isProfileOwner) dpm.clearProfileOwner(admin)
    }

    @Suppress("DEPRECATION")
    fun removeDeviceOwner() {
        if (isDeviceOwnerActive) dpm.clearDeviceOwnerApp(app.packageName)
    }
}