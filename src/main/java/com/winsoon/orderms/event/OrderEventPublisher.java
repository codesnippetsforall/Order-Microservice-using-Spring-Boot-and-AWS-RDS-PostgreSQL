package com.winsoon.orderms.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * Publishes Order events to AWS SQS
 * Decouples synchronous order operations from downstream services
 */
@Slf4j
@Service
public class OrderEventPublisher {

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url:}")
    private String queueUrl;

    /**
     * Publish order event to SQS queue
     * @param event The order event to publish
     * @return True if published successfully, False otherwise
     */
    public boolean publishEvent(OrderEvent event) {
        if (!isQueueConfigured()) {
            log.warn("SQS queue URL not configured. Event will not be published.");
            return false;
        }

        try {
            String messageBody = objectMapper.writeValueAsString(event);

            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId(buildMessageGroupId(event))  // For FIFO queues
                    .messageDeduplicationId(event.getEventId())  // For FIFO deduplication
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(sendMessageRequest);

            log.info("Order event published successfully - Event ID: {}, Message ID: {}, Event Type: {}",
                    event.getEventId(), response.messageId(), event.getEventType());

            return true;

        } catch (Exception e) {
            log.error("Failed to publish order event to SQS - Event ID: {}, Event Type: {}, Error: {}",
                    event.getEventId(), event.getEventType(), e.getMessage(), e);
            // Don't throw - let the operation complete even if event publishing fails
            return false;
        }
    }

    /**
     * Publish multiple events (batch)
     */
    public int publishBatch(java.util.List<OrderEvent> events) {
        int publishedCount = 0;
        for (OrderEvent event : events) {
            if (publishEvent(event)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    /**
     * Build message group ID for FIFO queue ordering
     * Orders are grouped by customer to maintain ordering within a customer's orders
     */
    private String buildMessageGroupId(OrderEvent event) {
        return "order-customer-" + event.getCustomerId();
    }

    /**
     * Check if SQS queue is configured
     */
    private boolean isQueueConfigured() {
        return queueUrl != null && !queueUrl.trim().isEmpty();
    }

    /**
     * Health check - verify SQS connectivity
     */
    public boolean isHealthy() {
        if (!isQueueConfigured()) {
            log.debug("SQS queue URL not configured");
            return false;
        }

        try {
            // Try to get queue attributes as a health check
            sqsClient.getQueueAttributes(req -> req.queueUrl(queueUrl));
            log.debug("SQS queue health check passed");
            return true;
        } catch (Exception e) {
            log.warn("SQS queue health check failed: {}", e.getMessage());
            return false;
        }
    }
}
