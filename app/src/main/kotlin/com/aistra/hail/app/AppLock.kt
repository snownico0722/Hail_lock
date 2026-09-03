package com.aistra.hail.app

import android.util.Base64
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.aistra.hail.HailApp.Companion.app
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.min

object AppLock {
    private const val KEY_PIN_HASH = "app_lock_pin_hash"
    private const val KEY_PIN_SALT = "app_lock_pin_salt"
    private const val KEY_FAILED_ATTEMPTS = "app_lock_failed_attempts"
    private const val KEY_LOCKED_UNTIL = "app_lock_locked_until"
    // The PIN protects local UI actions rather than high-value remote credentials.
    // Keep this deliberately modest so verification remains responsive on older devices.
    private const val ITERATIONS = 20_000
    private const val KEY_LENGTH_BITS = 256
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 8
    private const val MAX_LOCK_SECONDS = 30 * 60L

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(app) }

    val isEnabled: Boolean
        get() = preferences.contains(KEY_PIN_HASH) && preferences.contains(KEY_PIN_SALT)

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    fun setPin(pin: String) {
        require(isValidPin(pin))
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = derive(pin, salt)
        preferences.edit {
            putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKED_UNTIL)
        }
    }

    fun clearPin() {
        preferences.edit {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKED_UNTIL)
        }
    }

    fun verify(pin: String): VerificationResult {
        if (!isEnabled) return VerificationResult.Success
        remainingLockSeconds()?.let { return VerificationResult.Locked(it) }

        val salt = preferences.getString(KEY_PIN_SALT, null)?.let {
            runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
        } ?: return VerificationResult.Invalid
        val expected = preferences.getString(KEY_PIN_HASH, null)?.let {
            runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
        } ?: return VerificationResult.Invalid

        if (MessageDigest.isEqual(expected, derive(pin, salt))) {
            preferences.edit {
                remove(KEY_FAILED_ATTEMPTS)
                remove(KEY_LOCKED_UNTIL)
            }
            return VerificationResult.Success
        }

        val attempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (attempts < 5) {
            preferences.edit { putInt(KEY_FAILED_ATTEMPTS, attempts) }
            return VerificationResult.Invalid
        }

        val exponent = min(attempts - 5, 6)
        val lockSeconds = min(30L shl exponent, MAX_LOCK_SECONDS)
        preferences.edit {
            putInt(KEY_FAILED_ATTEMPTS, attempts)
            putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + lockSeconds * 1000)
        }
        return VerificationResult.Locked(lockSeconds)
    }

    private fun remainingLockSeconds(): Long? {
        val remainingMillis = preferences.getLong(KEY_LOCKED_UNTIL, 0) - System.currentTimeMillis()
        return if (remainingMillis > 0) (remainingMillis + 999) / 1000 else null
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    sealed interface VerificationResult {
        data object Success : VerificationResult
        data object Invalid : VerificationResult
        data class Locked(val remainingSeconds: Long) : VerificationResult
    }
}
