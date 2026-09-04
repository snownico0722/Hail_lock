package com.aistra.hail.ui.main

import android.app.ActivityManager
import android.os.Bundle
import android.view.Menu
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.aistra.hail.R
import com.aistra.hail.app.AppLock
import com.aistra.hail.app.HailData
import com.aistra.hail.app.UsageLimitData
import com.aistra.hail.databinding.ActivityMainBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.ui.lock.AppLockDialogs
import com.aistra.hail.utils.HPolicy
import com.aistra.hail.utils.HUI
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    lateinit var fab: ExtendedFloatingActionButton
    lateinit var appbar: AppBarLayout
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = initView()
        if (AppLock.isEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            binding.root.isVisible = false
            AppLockDialogs.showUnlock(
                activity = this,
                onSuccess = { binding.root.isVisible = true },
                onCancel = ::finishAndRemoveTask
            )
            return
        }
        if (!HailData.biometricLogin || BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS
        ) return
        binding.root.isVisible = false
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    HUI.showToast(errString)
                    finishAndRemoveTask()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    binding.root.isVisible = true
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.action_biometric))
            .setSubtitle(getString(R.string.msg_biometric)).setNegativeButtonText(getString(android.R.string.cancel))
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onResume() {
        super.onResume()
        if (UsageLimitData.backgroundHide) setTaskExcludedFromRecents(false)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (AppLock.isEnabled) {
            binding.root.isVisible = false
            finishAndRemoveTask()
        } else if (UsageLimitData.backgroundHide) {
            setTaskExcludedFromRecents(true)
        }
    }

    private fun setTaskExcludedFromRecents(excluded: Boolean) {
        val manager = getSystemService(ActivityManager::class.java) ?: return
        @Suppress("DEPRECATION")
        manager.appTasks.firstOrNull { it.taskInfo.id == taskId }?.setExcludeFromRecents(excluded)
    }

    private fun initView() = ActivityMainBinding.inflate(layoutInflater).apply {
        setContentView(root)
        setSupportActionBar(appBarMain.toolbar)
        fab = appBarMain.fab
        appbar = appBarMain.appBarLayout

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this@MainActivity)
        val appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_home, R.id.nav_apps, R.id.nav_usage_limits, R.id.nav_settings, R.id.nav_about
        ).build()
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav?.setupWithNavController(navController)
        navRail?.setupWithNavController(navController)

        val isRtl = isRtl
        val isLandscape = isLandscape
        appBarMain.appBarLayout.applyDefaultInsetter {
            paddingRelative(isRtl, start = !isLandscape, end = true, top = true)
        }
        bottomNav?.applyDefaultInsetter { paddingRelative(isRtl, start = true, end = true, bottom = true) }
        navRail?.applyDefaultInsetter { paddingRelative(isRtl, start = true, top = true, bottom = true) }
        fab.applyDefaultInsetter { marginRelative(isRtl, end = true, bottom = isLandscape) }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let { MenuCompat.setGroupDividerEnabled(it, true) }
        return super.onCreateOptionsMenu(menu)
    }

    fun ownerRemoveDialog() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.title_remove_owner).setMessage(R.string.msg_remove_owner)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                HPolicy.setOrganizationName()
                HPolicy.removeDeviceOwner()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    /* override fun onStop() {
        super.onStop()
        if (HailData.biometricLogin) finishAndRemoveTask()
    } */

    override fun onDestinationChanged(
        controller: NavController, destination: NavDestination, arguments: Bundle?
    ) {
        fab.tag = destination.id == R.id.nav_home
        if (fab.tag == true) fab.show() else fab.hide()
    }
}
