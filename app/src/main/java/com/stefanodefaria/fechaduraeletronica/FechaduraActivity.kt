package com.stefanodefaria.fechaduraeletronica

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.lang.Exception
import javax.crypto.SecretKey

private fun createPromptInfo(context: Context): BiometricPrompt.PromptInfo {
    return BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.bio_input_title))
        .setConfirmationRequired(false)
        .setNegativeButtonText(context.getString(R.string.cancel))
        .build()
}


private const val AUTHENTICATOR = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.BIOMETRIC_STRONG

class FechaduraActivity : Toaster, AppCompatActivity() {
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var wrappingKeyName: String
    private lateinit var wrappedDecryptionKeyName: String
    private lateinit var encryptedAwsCredentials: EncryptedData
    private lateinit var cryptoHelper: CryptoHelper
    private lateinit var ioTClientConfig: IoTClientConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ioTClientConfig = IoTClientConfig(
            getString(R.string.iot_client_endpoint),
            getString(R.string.iot_client_aws_region),
            getString(R.string.ios_client_topic),
            Integer.parseInt(getString(R.string.ios_client_qos)),
        )
        wrappingKeyName = getString(R.string.wrapping_key_name)
        wrappedDecryptionKeyName = getString(R.string.wrapped_key_name)
        encryptedAwsCredentials =
            EncryptedData.fromString(getString(R.string.encrypted_aws_credentials))

        cryptoHelper = CryptoHelper(wrappingKeyName)
        promptInfo = createPromptInfo(this)

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener { buttonOnClickListener() }
    }

    private fun buttonOnClickListener() {
        val canAuthenticateResult = BiometricManager.from(this).canAuthenticate(AUTHENTICATOR)
        if (canAuthenticateResult != BiometricManager.BIOMETRIC_SUCCESS) {
            toast(getString(R.string.device_unsupported_msg))
        }

        val wrappedDecryptionKey = getWrappedKeyFromSharedPrefs()

        if (wrappedDecryptionKey == null) {
            promptForPassword { password ->
                val decryptionKey = cryptoHelper.deriveDecryptionKeyFromPassword(password)

                getPlaintextAwsCredentials(decryptionKey)?.let { awsCredentials ->
                    biometricPromptForStoringDecryptionKey(decryptionKey) {
                        sendCommand(awsCredentials)
                    }
                }
            }
        } else {
            biometricPromptForUnwrappingDecryptionKey(wrappedDecryptionKey) { decryptionKey ->
                getPlaintextAwsCredentials(decryptionKey)?.let { sendCommand(it) }
            }
        }

    }

    private fun promptForPassword(callback: (password: String) -> Unit) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.password_prompt_title))
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.ok)) { _, _ -> callback(input.text.toString()) }
        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun biometricPromptForUnwrappingDecryptionKey(
        wrappedKey: EncryptedData,
        callback: (secretKey: SecretKey) -> Unit
    ) {

        val biometricPrompt = createBiometricPrompt {
            val unwrappingCipher = it.cryptoObject?.cipher!!
            val decryptionKey =
                cryptoHelper.unwrapKey(wrappedKey.ciphertext, unwrappingCipher)
            callback(decryptionKey)
        }

        val cipher = cryptoHelper.getInitializedCipherForUnwrapping(wrappedKey)
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun biometricPromptForStoringDecryptionKey(
        decryptionKey: SecretKey,
        callback: () -> Unit
    ) {
        val biometricPrompt = createBiometricPrompt {
            val wrappingCipher = it.cryptoObject?.cipher!!
            val wrappedKey = cryptoHelper.wrapKey(decryptionKey, wrappingCipher)
            saveWrappedKeyInSharedPrefs(wrappedKey)
            callback()
        }

        val cipher = cryptoHelper.getInitializedCipherForWrapping()
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun createBiometricPrompt(resultCallback: (BiometricPrompt.AuthenticationResult) -> Unit)
            : BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                resultCallback(result)
            }
        }

        return BiometricPrompt(this, executor, callback)
    }

    private fun sendCommand(awsCredentials: AwsCredentials) {
        val iotClient = IoTClient(awsCredentials, this, ioTClientConfig)
        iotClient.publishCommand()
    }

    private fun getPlaintextAwsCredentials(decryptionKey: SecretKey): AwsCredentials? {
        var awsCredentials: AwsCredentials? = null
        try {
            val decryptedCredentialsString =
                cryptoHelper.decryptData(encryptedAwsCredentials, decryptionKey)
            awsCredentials = AwsCredentials.fromString(decryptedCredentialsString)
        } catch (e: Exception) {
            toast(getString(R.string.incorrect_password))
        }
        return awsCredentials
    }

    private fun saveWrappedKeyInSharedPrefs(value: EncryptedData) {
        val editor = this.getSharedPreferences("SP", Activity.MODE_PRIVATE).edit()
        editor.putString(wrappedDecryptionKeyName, value.toString())
        editor.apply()
    }

    private fun getWrappedKeyFromSharedPrefs(): EncryptedData? {
        val stringValue = this.getSharedPreferences("SP", Activity.MODE_PRIVATE)
            .getString(wrappedDecryptionKeyName, null)

        if (stringValue != null)
            return EncryptedData.fromString(stringValue)
        return null
    }

    override fun toast(message: String) {
        this.runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun toast(stringId: Int) {
        toast(getString(stringId))
    }

}
