package com.stefanodefaria.fechaduraeletronica

import java.util.*

private const val ciphertextIvSeparator = "_"

data class EncryptedData(val ciphertext: ByteArray, val initializationVector: ByteArray) {

    companion object {
        fun fromString(string: String): EncryptedData {
            val splitParts = string.split(ciphertextIvSeparator)
            val cipherTextPart = splitParts[0]
            val ivPart = splitParts[1]
            val cipherText = Base64.getDecoder().decode(cipherTextPart)
            val iv = Base64.getDecoder().decode(ivPart)
            return EncryptedData(cipherText, iv)
        }
    }

    override fun toString(): String {
        return Base64.getEncoder().encodeToString(ciphertext) + ciphertextIvSeparator +
                Base64.getEncoder().encodeToString(initializationVector)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!initializationVector.contentEquals(other.initializationVector)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + initializationVector.contentHashCode()
        return result
    }
}
