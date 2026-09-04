package com.aistra.hail.services

import android.app.admin.DeviceAdminService
import com.aistra.hail.app.UsageLimitController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageLimitAdminService : DeviceAdminService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            while (isActive) {
                runCatching { UsageLimitController.tick(applicationContext) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
