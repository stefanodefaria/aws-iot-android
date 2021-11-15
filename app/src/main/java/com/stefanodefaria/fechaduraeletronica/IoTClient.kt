package com.stefanodefaria.fechaduraeletronica

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.regions.Region
import com.amazonaws.services.iotdata.AWSIotDataClient
import com.amazonaws.services.iotdata.model.PublishRequest
import org.jetbrains.anko.doAsync
import java.nio.ByteBuffer

data class IoTClientConfig(
    val endpoint: String,
    val region: String,
    val topic: String,
    val qos: Int
)

class IoTClient(
    awsCredentials: AwsCredentials,
    private val toaster: Toaster,
    private val iotClientConfig: IoTClientConfig
) {

    private val iotClient: AWSIotDataClient

    init {
        this.iotClient = createIotClient(awsCredentials)
    }

    private fun createIotClient(awsCredentials: AwsCredentials): AWSIotDataClient {

        val iotClient = AWSIotDataClient(awsCredentials)
        iotClient.setRegion(Region.getRegion(iotClientConfig.region))
        iotClient.endpoint = iotClientConfig.endpoint

        return iotClient
    }

    fun publishCommand(command: String = "{}") {
        toaster.toast(R.string.sending_command)

        val publishRequest = PublishRequest()
        publishRequest.topic = iotClientConfig.topic
        publishRequest.qos = iotClientConfig.qos
        publishRequest.payload = ByteBuffer.wrap(command.toByteArray(Charsets.UTF_8))

        doAsync {
            try {
                iotClient.publish(publishRequest)
                toaster.toast(R.string.command_successful)
            } catch (e: AmazonClientException) {
                toaster.toast(toaster.getString(R.string.error_template) + e.message)
            } catch (e: AmazonServiceException) {
                toaster.toast(toaster.getString(R.string.error_template) + e.message)
            }
        }

    }

}