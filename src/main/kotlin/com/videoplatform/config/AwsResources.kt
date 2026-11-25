package com.videoplatform.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.Event
import software.amazon.awssdk.services.s3.model.FilterRule
import software.amazon.awssdk.services.s3.model.FilterRuleName
import software.amazon.awssdk.services.s3.model.NotificationConfiguration
import software.amazon.awssdk.services.s3.model.NotificationConfigurationFilter
import software.amazon.awssdk.services.s3.model.QueueConfiguration
import software.amazon.awssdk.services.s3.model.S3KeyFilter
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

// provision=true crea los recursos (solo local); en producción lo hace IaC, y en AWS real S3 necesita además una queue policy hacia SQS.
@Component
class AwsResources(
    private val s3: S3Client,
    private val sqs: SqsClient,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    final lateinit var uploadedQueueUrl: String
        private set
    final lateinit var uploadedDlqUrl: String
        private set
    final lateinit var viewedQueueUrl: String
        private set
    final lateinit var viewedDlqUrl: String
        private set

    @PostConstruct
    fun init() {
        if (props.aws.provision) {
            ensureBucket()
        }
        val (uploaded, uploadedDlq) = ensureQueuePair(props.queues.uploaded, props.queues.uploadedDlq)
        val (viewed, viewedDlq) = ensureQueuePair(props.queues.viewed, props.queues.viewedDlq)
        uploadedQueueUrl = uploaded
        uploadedDlqUrl = uploadedDlq
        viewedQueueUrl = viewed
        viewedDlqUrl = viewedDlq

        if (props.aws.provision) {
            configureUploadNotification()
        }
        log.info("Recursos AWS listos: bucket={}, colas={}", props.aws.bucket, listOf(uploaded, viewed))
    }

    private fun ensureBucket() {
        try {
            s3.createBucket { it.bucket(props.aws.bucket) }
        } catch (e: BucketAlreadyOwnedByYouException) {
            log.debug("El bucket {} ya existe", props.aws.bucket)
        } catch (e: BucketAlreadyExistsException) {
            log.debug("El bucket {} ya existe", props.aws.bucket)
        }
    }

    private fun ensureQueuePair(name: String, dlqName: String): Pair<String, String> {
        val dlqUrl = ensureQueue(dlqName, dlqArn = null)
        val mainUrl = ensureQueue(name, dlqArn = if (props.aws.provision) queueArn(dlqUrl) else null)
        return mainUrl to dlqUrl
    }

    private fun ensureQueue(name: String, dlqArn: String?): String {
        if (!props.aws.provision) {
            return sqs.getQueueUrl { it.queueName(name) }.queueUrl()
        }
        val attributes = mutableMapOf(
            QueueAttributeName.VISIBILITY_TIMEOUT to props.queues.visibilityTimeoutSeconds.toString()
        )
        if (dlqArn != null) {
            attributes[QueueAttributeName.REDRIVE_POLICY] =
                """{"deadLetterTargetArn":"$dlqArn","maxReceiveCount":"${props.queues.maxReceiveCount}"}"""
        }
        return sqs.createQueue { it.queueName(name).attributes(attributes) }.queueUrl()
    }

    private fun queueArn(queueUrl: String): String =
        sqs.getQueueAttributes { it.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN) }
            .attributes()[QueueAttributeName.QUEUE_ARN]
            ?: error("No se pudo obtener el ARN de $queueUrl")

    private fun configureUploadNotification() {
        val prefixFilter = NotificationConfigurationFilter.builder()
            .key(
                S3KeyFilter.builder()
                    .filterRules(FilterRule.builder().name(FilterRuleName.PREFIX).value("videos/").build())
                    .build()
            )
            .build()
        val queueConfig = QueueConfiguration.builder()
            .queueArn(queueArn(uploadedQueueUrl))
            .events(Event.S3_OBJECT_CREATED)
            .filter(prefixFilter)
            .build()
        val configuration = NotificationConfiguration.builder().queueConfigurations(queueConfig).build()
        s3.putBucketNotificationConfiguration {
            it.bucket(props.aws.bucket).notificationConfiguration(configuration)
        }
    }
}
