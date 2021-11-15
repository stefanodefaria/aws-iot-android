package com.stefanodefaria.fechaduraeletronica

/**
 * Use this function to encrypt your AWS credentials with a password
 */
fun main() {
    print("Crete a password: ")
    val password = readLine()
    print("Confirm password: ")
    val passwordConfirmation = readLine()
    assert(password.equals(passwordConfirmation))

    print("Enter AWS Access Key ID: ")
    val accessKeyId = readLine()
    print("Enter AWS Secret Access Key: ")
    val secretAccessKey = readLine()

    val credentials = AwsCredentials(accessKeyId!!, secretAccessKey!!)

    val cryptographyManager = CryptoHelper("wrappingKeyName")
    val encryptionKey = cryptographyManager.deriveDecryptionKeyFromPassword(password!!)

    val encryptedData = cryptographyManager.encryptData(credentials.toString(), encryptionKey)

    cryptographyManager.decryptData(encryptedData, encryptionKey) // test

    println("encrypted credentials: $encryptedData")
}