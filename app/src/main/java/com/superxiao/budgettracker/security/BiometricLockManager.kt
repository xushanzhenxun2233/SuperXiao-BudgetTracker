package com.superxiao.budgettracker.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricLockManager {
    private const val PREF_NAME = "budget_security_prefs"
    private const val KEY_LOCK_ENABLED = "biometric_lock_enabled"

    /**
     * Android 11+ (API 30+) 允许 BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL；
     * Android 8-10 (API 26-29) 只能使用 BIOMETRIC_WEAK | DEVICE_CREDENTIAL，否则会抛 IllegalArgumentException。
     */
    private fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun isLockEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }

    fun canAuthenticate(context: Context): Boolean {
        return try {
            val manager = BiometricManager.from(context)
            manager.canAuthenticate(allowedAuthenticators()) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "验证身份以进入",
        subtitle: String = "请使用指纹、人脸或锁屏密码解锁超级魈的记账工具",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(allowedAuthenticators())
                .build()

            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "当前设备暂不支持生物识别验证")
        }
    }
}
