package com.videoplatform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
class AwsConfig(private val props: AppProperties) {

    private val region: Region = Region.of(props.aws.region)
    private val endpoint: URI? = props.aws.endpoint?.takeIf { it.isNotBlank() }?.let { URI.create(it) }

    private val credentials: AwsCredentialsProvider = run {
        val accessKey = props.aws.accessKey
        val secretKey = props.aws.secretKey
        if (!accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        } else {
            DefaultCredentialsProvider.create()
        }
    }

    @Bean
    fun s3Client(): S3Client {
        val builder = S3Client.builder()
            .region(region)
            .credentialsProvider(credentials)
            .forcePathStyle(endpoint != null)
        endpoint?.let { builder.endpointOverride(it) }
        return builder.build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        val builder = S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(endpoint != null).build()
            )
        endpoint?.let { builder.endpointOverride(it) }
        return builder.build()
    }

    @Bean
    fun sqsClient(): SqsClient {
        val builder = SqsClient.builder()
            .region(region)
            .credentialsProvider(credentials)
        endpoint?.let { builder.endpointOverride(it) }
        return builder.build()
    }
}
