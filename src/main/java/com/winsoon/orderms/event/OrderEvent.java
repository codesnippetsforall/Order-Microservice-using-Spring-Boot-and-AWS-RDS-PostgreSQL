package com.winsoon.orderms.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Event - Published to SQS when order changes occur
 * Other microservices subscribe to these events
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("event_type")
    private String eventType;  // CREATED, UPDATED, DELETED, STATUS_CHANGED

    @JsonProperty("event_timestamp")
    private LocalDateTime eventTimestamp;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_number")
    private String orderNumber;

    @JsonProperty("customer_id")
    private Long customerId;

    @JsonProperty("order_status")
    private String orderStatus;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("shipping_address")
    private String shippingAddress;

    @JsonProperty("source_service")
    private String sourceService;  // "orderms"

    @JsonProperty("correlation_id")
    private String correlationId;  // For tracking across services

    public OrderEvent(String eventType, Long orderId, String orderNumber, Long customerId,
                      String orderStatus, BigDecimal totalAmount, String shippingAddress) {
        this.eventId = generateEventId();
        this.eventType = eventType;
        this.eventTimestamp = LocalDateTime.now();
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.shippingAddress = shippingAddress;
        this.sourceService = "orderms";
        this.correlationId = generateCorrelationId();
    }

    private static String generateEventId() {
        return "EVT-" + System.currentTimeMillis() + "-" + System.nanoTime();
    }

    private static String generateCorrelationId() {
        return "CORR-" + System.currentTimeMillis();
    }
}
