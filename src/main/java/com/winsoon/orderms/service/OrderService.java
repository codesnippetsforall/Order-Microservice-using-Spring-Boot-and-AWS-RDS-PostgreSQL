package com.winsoon.orderms.service;

import com.winsoon.orderms.dto.OrderDTO;
import com.winsoon.orderms.entity.Order;
import com.winsoon.orderms.event.OrderEvent;
import com.winsoon.orderms.event.OrderEventPublisher;
import com.winsoon.orderms.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Order Service
 * Business logic for order management
 */
@Slf4j
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventPublisher eventPublisher;
    
    /**
     * Create a new order
     */
    public OrderDTO createOrder(OrderDTO orderDTO) {
        log.info("Creating new order for customer: {}", orderDTO.getCustomerId());

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(orderDTO.getCustomerId())
                .totalAmount(orderDTO.getTotalAmount())
                .status(com.winsoon.orderms.entity.OrderStatus.PENDING)
                .shippingAddress(orderDTO.getShippingAddress())
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully with ID: {}", savedOrder.getOrderId());

        // Publish ORDER_CREATED event to SQS
        publishOrderEvent("ORDER_CREATED", savedOrder);

        return convertToDTO(savedOrder);
    }
    
    /**
     * Get order by ID
     */
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long orderId) {
        log.info("Fetching order with ID: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));
        
        return convertToDTO(order);
    }
    
    /**
     * Get order by order number
     */
    @Transactional(readOnly = true)
    public OrderDTO getOrderByNumber(String orderNumber) {
        log.info("Fetching order with number: {}", orderNumber);
        
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found with number: " + orderNumber));
        
        return convertToDTO(order);
    }
    
    /**
     * Get all orders by customer ID
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByCustomerId(Long customerId) {
        log.info("Fetching orders for customer ID: {}", customerId);
        
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all orders
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrders() {
        log.info("Fetching all orders");
        
        return orderRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Update order status
     */
    public OrderDTO updateOrderStatus(Long orderId, String status) {
        log.info("Updating order {} status to: {}", orderId, status);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        order.setStatus(com.winsoon.orderms.entity.OrderStatus.valueOf(status));
        Order updatedOrder = orderRepository.save(order);

        log.info("Order status updated successfully");

        // Publish ORDER_STATUS_CHANGED event to SQS
        publishOrderEvent("ORDER_STATUS_CHANGED", updatedOrder);

        return convertToDTO(updatedOrder);
    }
    
    /**
     * Delete order
     */
    public void deleteOrder(Long orderId) {
        log.info("Deleting order with ID: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        orderRepository.deleteById(orderId);
        log.info("Order deleted successfully");

        // Publish ORDER_DELETED event to SQS
        publishOrderEvent("ORDER_DELETED", order);
    }
    
    /**
     * Get orders within date range
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersBetweenDates(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Fetching orders between {} and {}", startDate, endDate);
        
        return orderRepository.findOrdersBetweenDates(startDate, endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Generate unique order number
     */
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }

    /**
     * Publish order event to SQS asynchronously
     */
    private void publishOrderEvent(String eventType, Order order) {
        try {
            OrderEvent event = new OrderEvent(
                    eventType,
                    order.getOrderId(),
                    order.getOrderNumber(),
                    order.getCustomerId(),
                    order.getStatus().toString(),
                    order.getTotalAmount(),
                    order.getShippingAddress()
            );

            boolean published = eventPublisher.publishEvent(event);
            if (published) {
                log.info("Order event published successfully - Event: {}, Order ID: {}", eventType, order.getOrderId());
            } else {
                log.warn("Failed to publish order event - Event: {}, Order ID: {}", eventType, order.getOrderId());
            }
        } catch (Exception e) {
            log.error("Error publishing order event - Event: {}, Order ID: {}, Error: {}",
                    eventType, order.getOrderId(), e.getMessage());
        }
    }

    /**
     * Convert Order entity to OrderDTO
     */
    private OrderDTO convertToDTO(Order order) {
        return OrderDTO.builder()
                .orderId(order.getOrderId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().toString())
                .shippingAddress(order.getShippingAddress())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
