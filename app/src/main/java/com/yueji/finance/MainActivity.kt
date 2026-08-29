package com.yueji.finance

import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yueji.finance.data.SettingsRepository
import com.yueji.finance.ui.YueJiApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    private var appLockEnabled = false
    private var authenticatedThisForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                appLockEnabled = settings.appLockEnabled
                if (settings.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent { YueJiApp() }
        window.decorView.post(::requestHighestRefreshRate)
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.post(::requestHighestRefreshRate)
    }

    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        val maxAdvertisedRate = targetDisplay?.supportedModes?.maxOfOrNull { it.refreshRate } ?: return
        val requestedRate = maxAdvertisedRate
        val attributes = window.attributes
        // preferredRefreshRate is ignored whenever preferredDisplayModeId is non-zero.
        // We only need a frame-rate preference, so leave resolution/mode selection to Android.
        attributes.preferredDisplayModeId = 0
        attributes.preferredRefreshRate = requestedRate
        window.attributes = attributes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.decorView.requestFrameRateRecursively(requestedRate)
            window.setFrameRateBoostOnTouchEnabled(true)
            window.setFrameRatePowerSavingsBalanced(false)
        }
    }

    private fun View.requestFrameRateRecursively(rate: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) requestedFrameRate = rate
        if (this is ViewGroup) {
            for (index in 0 until childCount) getChildAt(index).requestFrameRateRecursively(rate)
        }
    }

    override fun onStart() {
        super.onStart()
        if (appLockEnabled && !authenticatedThisForeground) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authenticatedThisForeground = true }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) finish() }
                }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("解锁月迹").setSubtitle("验证设备凭据以查看财务数据").setAllowedAuthenticators(authenticators).build())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) authenticatedThisForeground = false
    }
}
