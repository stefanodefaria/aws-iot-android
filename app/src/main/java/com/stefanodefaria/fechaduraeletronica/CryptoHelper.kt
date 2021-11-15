package com.stefanodefaria.fechaduraeletronica

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val PBKDF_ITER_COUNT = 10000
private val PBKDF_SALT =
    byteArrayOf(10, 13, -13, 3, -102, 23, 125, 82, 127, -121, -44, 43, -122, -105, -68, -50)

private const val KEY_SIZE: Int = 256
private const val IV_SIZE: Int = 128
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
private const val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"

class CryptoHelper(private val wrappingKeyName: String) {

    fun deriveDecryptionKeyFromPassword(password: String): SecretKey {
        val spec: KeySpec =
            PBEKeySpec(password.toCharArray(), PBKDF_SALT, PBKDF_ITER_COUNT, KEY_SIZE)
        val derivedKey = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            .generateSecret(spec)

        return SecretKeySpec(derivedKey.encoded, ENCRYPTION_ALGORITHM)
    }

    fun encryptData(plaintext: String, encryptionKey: SecretKey): EncryptedData {
        val cipher = getCipher()
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return EncryptedData(ciphertext, cipher.iv)
    }

    fun decryptData(encryptedData: EncryptedData, decryptionKey: SecretKey): String {
        val cipher = getCipher()
        val gcmSpec = GCMParameterSpec(IV_SIZE, encryptedData.initializationVector)
        cipher.init(Cipher.DECRYPT_MODE, decryptionKey, gcmSpec)
        val plaintext = cipher.doFinal(encryptedData.ciphertext)
        return String(plaintext, Charset.forName("UTF-8"))
    }

    fun getInitializedCipherForWrapping(): Cipher {
        val cipher = getCipher()
        val secretKey = getOrCreateWrappingKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    fun getInitializedCipherForUnwrapping(encryptedData: EncryptedData): Cipher {
        val cipher = getCipher()
        val wrappingKey = getOrCreateWrappingKey()
        val gcmSpec = GCMParameterSpec(IV_SIZE, encryptedData.initializationVector)
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey, gcmSpec)
        return cipher
    }

    fun wrapKey(secretKey: SecretKey, cipher: Cipher): EncryptedData {
        val ciphertext = cipher.doFinal(secretKey.encoded)
        return EncryptedData(ciphertext, cipher.iv)
    }

    fun unwrapKey(ciphertext: ByteArray, cipher: Cipher): SecretKey {
        val unwrappedKey = cipher.doFinal(ciphertext)
        return SecretKeySpec(unwrappedKey, ENCRYPTION_ALGORITHM)
    }

    private fun getCipher(): Cipher {
        val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        return Cipher.getInstance(transformation)
    }

    private fun getOrCreateWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(wrappingKeyName, null) as SecretKey? ?: createWrappingKey()
    }

    private fun createWrappingKey(): SecretKey {
        val paramsBuilder = KeyGenParameterSpec.Builder(
            wrappingKeyName,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(ENCRYPTION_BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(true)
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

}