package com.winsoon.orderms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order Data Transfer Object
 * Used for transferring order data in API requests/responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDTO {
    
    private Long orderId;
    private String orderNumber;
    private Long customerId;
    private LocalDateTime orderDate;
    private BigDecimal totalAmount;
    private String status;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
