package com.winsoon.orderms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * AWS SQS Configuration
 * Creates SqsClient bean for publishing messages to SQS queues
 */
@Slf4j
@Configuration
public class SqsConfig {

    @Value("${aws.region:ap-south-2}")
    private String awsRegion;

    @Value("${aws.sqs.queue-url:}")
    private String queueUrl;

    /**
     * Create SqsClient bean
     * Uses default credential chain (IAM role on ECS, env vars locally, etc.)
     */
    @Bean
    public SqsClient sqsClient() {
        log.info("Initializing SQS client for region: {}", awsRegion);

        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(awsRegion));

        SqsClient client = builder.build();
        log.info("SQS client initialized successfully");

        if (queueUrl != null && !queueUrl.isEmpty()) {
            log.info("SQS queue configured: {} (first 50 chars)", queueUrl.substring(0, Math.min(50, queueUrl.length())));
        } else {
            log.warn("SQS queue URL not configured - event publishing will be disabled");
        }

        return client;
    }
}
