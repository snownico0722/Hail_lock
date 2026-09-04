package com.aistra.hail.ui.usage

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.aistra.hail.BuildConfig
import com.aistra.hail.R
import com.aistra.hail.app.UsageLimitController
import com.aistra.hail.app.UsageLimitData
import com.aistra.hail.app.UsageLimitTracker
import com.aistra.hail.ui.main.MainFragment
import com.aistra.hail.ui.theme.AppTheme
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay

class UsageLimitsFragment : MainFragment() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    UsageLimitsScreen()
                }
            }
        }

    @Composable
    private fun UsageLimitsScreen() {
        var refreshVersion by remember { mutableIntStateOf(0) }
        var enabled by remember { mutableStateOf(UsageLimitData.enabled) }
        var backgroundHide by remember { mutableStateOf(UsageLimitData.backgroundHide) }
        var limits by remember(refreshVersion) { mutableStateOf(UsageLimitData.appLimits()) }
        var totalLimit by remember(refreshVersion) { mutableIntStateOf(UsageLimitData.totalLimitMinutes) }
        var snapshot by remember(refreshVersion) {
            mutableStateOf(
                if (UsageLimitTracker.hasUsageAccess(requireContext())) UsageLimitTracker.snapshot()
                else UsageLimitTracker.Snapshot(0L, emptyMap(), 0L, null)
            )
        }
        val hasUsageAccess = UsageLimitTracker.hasUsageAccess(requireContext())
        val isOwner = HPolicy.isDeviceOwnerActive

        LaunchedEffect(refreshVersion, hasUsageAccess) {
            while (true) {
                if (hasUsageAccess && limits.isNotEmpty()) {
                    snapshot = UsageLimitTracker.snapshot()
                }
                delay(3_000L)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (!isOwner) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.usage_limit_owner_required), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.usage_limit_owner_required_summary))
                        }
                    }
                }
            }

            if (!hasUsageAccess) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.usage_limit_usage_access_required), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.usage_limit_usage_access_summary))
                            TextButton(onClick = {
                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }) {
                                Text(stringResource(R.string.usage_limit_grant_access))
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.usage_limit_enable)) },
                        supportingContent = { Text(stringResource(R.string.usage_limit_enable_summary)) },
                        leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                enabled = isOwner,
                                onCheckedChange = { value ->
                                    if (value && !UsageLimitTracker.hasUsageAccess(requireContext())) {
                                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                        return@Switch
                                    }
                                    UsageLimitData.enabled = value
                                    enabled = value
                                    if (!value) UsageLimitController.releaseAllEnforced()
                                    if (value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            requireContext(), Manifest.permission.POST_NOTIFICATIONS
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.usage_limit_background_hide)) },
                        supportingContent = { Text(stringResource(R.string.usage_limit_background_hide_summary)) },
                        leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = backgroundHide,
                                onCheckedChange = { value ->
                                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit {
                                        putBoolean(UsageLimitData.BACKGROUND_HIDE, value)
                                    }
                                    backgroundHide = value
                                }
                            )
                        }
                    )
                }
            }

            item {
                TotalLimitCard(
                    totalLimitMinutes = totalLimit,
                    usedMs = snapshot.totalMs,
                    onEdit = {
                        showMinutesDialog(
                            title = getString(R.string.usage_limit_total),
                            initialMinutes = totalLimit,
                            allowZero = true
                        ) { minutes ->
                            UsageLimitData.totalLimitMinutes = minutes
                            totalLimit = minutes
                            refreshVersion++
                        }
                    }
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(R.string.usage_limit_apps),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    TextButton(onClick = {
                        showAppPicker {
                            limits = UsageLimitData.appLimits()
                            UsageLimitTracker.invalidate()
                            refreshVersion++
                        }
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text(stringResource(R.string.usage_limit_add_app))
                    }
                }
            }

            if (limits.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.usage_limit_no_apps),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(limits.entries.toList(), key = { it.key }) { entry ->
                    AppLimitCard(
                        packageName = entry.key,
                        limitMinutes = entry.value,
                        usedMs = snapshot.perAppMs[entry.key] ?: 0L,
                        onEdit = {
                            showMinutesDialog(
                                title = appName(entry.key),
                                initialMinutes = entry.value,
                                allowZero = false
                            ) { minutes ->
                                UsageLimitData.setAppLimit(entry.key, minutes)
                                limits = UsageLimitData.appLimits()
                                refreshVersion++
                            }
                        },
                        onRemove = {
                            UsageLimitController.removePackage(entry.key)
                            limits = UsageLimitData.appLimits()
                            refreshVersion++
                        }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.usage_limit_total_summary),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    @Composable
    private fun TotalLimitCard(totalLimitMinutes: Int, usedMs: Long, onEdit: () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.usage_limit_total), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                }
                if (totalLimitMinutes <= 0) {
                    Text(stringResource(R.string.usage_limit_not_set))
                } else {
                    val usedMinutes = usedMs / 60_000L
                    Text(stringResource(R.string.usage_limit_minutes_progress, usedMinutes, totalLimitMinutes))
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (usedMs.toFloat() / (totalLimitMinutes * 60_000f)).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    @Composable
    private fun AppLimitCard(
        packageName: String,
        limitMinutes: Int,
        usedMs: Long,
        onEdit: () -> Unit,
        onRemove: () -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onEdit),
                    headlineContent = { Text(appName(packageName)) },
                    supportingContent = {
                        Text(stringResource(R.string.usage_limit_minutes_progress, usedMs / 60_000L, limitMinutes))
                    },
                    trailingContent = {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.usage_limit_remove_app))
                        }
                    }
                )
                LinearProgressIndicator(
                    progress = (usedMs.toFloat() / (limitMinutes * 60_000f)).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    private fun showAppPicker(onChanged: () -> Unit) {
        val existing = UsageLimitData.appLimits().keys
        val apps = HPackages.getInstalledApplications()
            .asSequence()
            .filter { it.packageName != BuildConfig.APPLICATION_ID }
            .filter { it.packageName !in existing }
            .filter { requireContext().packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(requireContext().packageManager).toString().lowercase() }
            .toList()
        if (apps.isEmpty()) return
        val labels = apps.map { it.loadLabel(requireContext().packageManager).toString() }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.usage_limit_add_app)
            .setItems(labels) { _, which ->
                UsageLimitData.setAppLimit(apps[which].packageName, UsageLimitData.DEFAULT_APP_LIMIT_MINUTES)
                UsageLimitTracker.invalidate()
                onChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMinutesDialog(
        title: String,
        initialMinutes: Int,
        allowZero: Boolean,
        onSave: (Int) -> Unit
    ) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(initialMinutes.toString())
            selectAll()
            setPadding(48, 12, 48, 12)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(
                if (allowZero) getString(R.string.usage_limit_minutes_input_zero)
                else getString(R.string.usage_limit_minutes_input)
            )
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text?.toString()?.toIntOrNull()
                val valid = if (allowZero) value != null && value >= 0 else value != null && value > 0
                if (!valid) {
                    input.error = getString(R.string.usage_limit_minutes_invalid)
                    return@setOnClickListener
                }
                onSave(value!!)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun appName(packageName: String): String =
        HPackages.getApplicationInfoOrNull(packageName)
            ?.loadLabel(requireContext().packageManager)?.toString() ?: packageName
}
