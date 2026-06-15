package com.lunastratos.simplememo

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * 앱 잠금 화면. 생체 인증 또는 기기 잠금 자격으로 해제한다.
 * 인증을 취소하면 앱을 닫는다.
 */
class LockActivity : AppCompatActivity() {

    private var promptShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        findViewById<Button>(R.id.unlock_button).setOnClickListener { showPrompt() }
    }

    override fun onResume() {
        super.onResume()
        // 잠금 설정이 풀려 있거나 인증 수단이 사라진 경우 잠그지 않는다
        val can = BiometricManager.from(this)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            finish()
            return
        }
        if (!promptShown) {
            promptShown = true
            showPrompt()
        }
    }

    private fun showPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED,
                        -> finishAffinity()
                        // 그 외 오류는 화면의 잠금 해제 버튼으로 재시도
                        else -> Unit
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAffinity()
    }
}
