package com.aistra.hail.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailData
import com.aistra.hail.app.UsageLimitController
import com.aistra.hail.services.AutoFreezeService
import com.aistra.hail.utils.HSystem

class AutoFreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if ((inputData.getBoolean(HailData.ACTION_LOCK, true)
                    && HSystem.isInteractive(applicationContext))
            || isSkipWhileCharging(applicationContext)
        ) return Result.success() // Not stopping the AutoFreezeService here. The worker will run at some point. Then we'll stop the Service

        // Resolve the non-freeze skip conditions first. If an eligible app is already
        // suspended by the usage limiter, this auto-freeze action upgrades that state
        // to an ordinary Hail freeze so the next daily reset will not undo it.
        val eligible = HailData.checkedList.filter { !isPolicySkipped(applicationContext, it) }
        UsageLimitController.promoteExternalFreeze(eligible.map { it.packageName })
        val checkedList = eligible.filterNot { AppManager.isAppFrozen(it.packageName) }

        val result = AppManager.setListFrozen(true, *checkedList.toTypedArray())
        return if (result == null) {
            Result.failure()
        } else {
            app.setAutoFreezeService()
            Result.success()
        }
    }

    private fun isSkipWhileCharging(context: Context): Boolean =
        HailData.skipWhileCharging && HSystem.isCharging(context)

    private fun isPolicySkipped(context: Context, appInfo: AppInfo): Boolean =
        (HailData.skipForegroundApp && HSystem.isForegroundApp(
            context, appInfo.packageName
        )) || (HailData.skipNotifyingApp && AutoFreezeService.instance.activeNotifications.any {
            it.packageName == appInfo.packageName
        }) || appInfo.whitelisted
}
