package com.example.adamapplock.protection

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.adamapplock.R
import java.util.UUID

class BiometricUnlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        if (requestId.isNullOrBlank()) {
            finish()
            return
        }

        val mgr = BiometricManager.from(this)
        val canAuth = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            notifyResult(requestId, false)
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    notifyResult(requestId, true)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    notifyResult(requestId, false)
                    finish()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_prompt_use_passcode))
            .build()

        prompt.authenticate(info)
    }

    private fun notifyResult(requestId: String, success: Boolean) {
        val callbacks = pendingCallbacks.remove(requestId) ?: return
        callbacks.forEach { it.invoke(success) }
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "extra_request_id"
        private val pendingCallbacks = mutableMapOf<String, MutableList<(Boolean) -> Unit>>()

        fun launch(context: Context, onResult: (Boolean) -> Unit) {
            val requestId = UUID.randomUUID().toString()
            pendingCallbacks.getOrPut(requestId) { mutableListOf() }.add(onResult)

            val intent = Intent(context, BiometricUnlockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_HISTORY
                )
                putExtra(EXTRA_REQUEST_ID, requestId)
            }

            context.startActivity(intent)
        }
    }
}
