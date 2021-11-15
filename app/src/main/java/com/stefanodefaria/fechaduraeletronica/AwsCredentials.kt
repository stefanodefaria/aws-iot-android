package com.stefanodefaria.fechaduraeletronica

import com.amazonaws.auth.AWSCredentials

private const val SEPARATOR = "_"

class AwsCredentials(private val accessKeyId: String, private val secretAccessKey: String) :
    AWSCredentials {
    companion object {
        fun fromString(string: String): AwsCredentials {
            val splitParts = string.split(SEPARATOR)
            val accessKeyId = splitParts[0]
            val secretAccessKey = splitParts[1]
            return AwsCredentials(accessKeyId, secretAccessKey)
        }
    }

    override fun toString(): String {
        return accessKeyId + SEPARATOR + secretAccessKey
    }

    override fun getAWSAccessKeyId(): String {
        return this.accessKeyId
    }

    override fun getAWSSecretKey(): String {
        return this.secretAccessKey
    }
}