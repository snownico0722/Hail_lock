package com.aistra.hail.ui.lock

import android.app.Activity
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import androidx.annotation.StringRes
import com.aistra.hail.R
import com.aistra.hail.app.AppLock
import com.aistra.hail.databinding.DialogInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object AppLockDialogs {
    fun showUnlock(activity: Activity, onSuccess: () -> Unit, onCancel: () -> Unit) {
        showPinEntry(
            activity = activity,
            title = R.string.app_pin_unlock,
            hint = R.string.app_pin,
            cancelable = false,
            validate = { pin -> verificationError(activity, AppLock.verify(pin)) },
            onAccepted = { onSuccess() },
            onCancel = onCancel
        )
    }

    fun showVerify(activity: Activity, @StringRes title: Int, onSuccess: () -> Unit) {
        showPinEntry(
            activity = activity,
            title = title,
            hint = R.string.app_pin_current,
            validate = { pin -> verificationError(activity, AppLock.verify(pin)) },
            onAccepted = { onSuccess() }
        )
    }

    fun showCreate(activity: Activity, onCreated: () -> Unit) {
        showPinEntry(
            activity = activity,
            title = R.string.app_pin_new,
            hint = R.string.app_pin_new,
            validate = { pin ->
                if (AppLock.isValidPin(pin)) null else activity.getString(R.string.msg_pin_requirements)
            },
            onAccepted = { newPin ->
                showPinEntry(
                    activity = activity,
                    title = R.string.app_pin_confirm,
                    hint = R.string.app_pin_confirm,
                    validate = { confirmation ->
                        if (confirmation == newPin) null else activity.getString(R.string.msg_pin_mismatch)
                    },
                    onAccepted = {
                        AppLock.setPin(newPin)
                        onCreated()
                    }
                )
            }
        )
    }

    private fun showPinEntry(
        activity: Activity,
        @StringRes title: Int,
        @StringRes hint: Int,
        cancelable: Boolean = true,
        validate: (String) -> CharSequence?,
        onAccepted: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val binding = DialogInputBinding.inflate(activity.layoutInflater)
        binding.inputLayout.setHint(hint)
        binding.editText.apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            setSingleLine()
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setCancelable(cancelable)
            .create()
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnShowListener {
            binding.editText.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = binding.editText.text?.toString().orEmpty()
                val error = validate(pin)
                if (error == null) {
                    dialog.dismiss()
                    onAccepted(pin)
                } else {
                    binding.inputLayout.error = error
                    binding.editText.text?.clear()
                }
            }
        }
        dialog.show()
    }

    private fun verificationError(
        activity: Activity, result: AppLock.VerificationResult
    ): CharSequence? = when (result) {
        AppLock.VerificationResult.Success -> null
        AppLock.VerificationResult.Invalid -> activity.getString(R.string.msg_pin_incorrect)
        is AppLock.VerificationResult.Locked ->
            activity.getString(R.string.msg_pin_locked, result.remainingSeconds)
    }
}
