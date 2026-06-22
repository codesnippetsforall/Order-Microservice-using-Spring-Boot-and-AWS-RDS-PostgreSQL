package com.winsoon.orderms.controller;

import com.winsoon.orderms.dto.OrderDTO;
import com.winsoon.orderms.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Controller
 * REST API endpoints for order management
 */
@Slf4j
@RestController
@RequestMapping("/orders")
@CrossOrigin(origins = "*", maxAge = 3600)
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    /**
     * Create a new order
     */
    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@RequestBody OrderDTO orderDTO) {
        log.info("POST /orders - Creating new order");
        OrderDTO createdOrder = orderService.createOrder(orderDTO);
        return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
    }
    
    /**
     * Get order by ID
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long orderId) {
        log.info("GET /orders/{} - Fetching order", orderId);
        OrderDTO order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Get order by order number
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderDTO> getOrderByNumber(@PathVariable String orderNumber) {
        log.info("GET /orders/number/{} - Fetching order by number", orderNumber);
        OrderDTO order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(order);
    }
    
    /**
     * Get all orders
     */
    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        log.info("GET /orders - Fetching all orders");
        List<OrderDTO> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders by customer ID
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByCustomerId(@PathVariable Long customerId) {
        log.info("GET /orders/customer/{} - Fetching orders for customer", customerId);
        List<OrderDTO> orders = orderService.getOrdersByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Get orders between dates
     */
    @GetMapping("/search/date-range")
    public ResponseEntity<List<OrderDTO>> getOrdersBetweenDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("GET /orders/search/date-range - Fetching orders between {} and {}", startDate, endDate);
        List<OrderDTO> orders = orderService.getOrdersBetweenDates(startDate, endDate);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * Update order status
     */
    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(@PathVariable Long orderId, @RequestParam String status) {
        log.info("PUT /orders/{}/status - Updating status to {}", orderId, status);
        OrderDTO updatedOrder = orderService.updateOrderStatus(orderId, status);
        return ResponseEntity.ok(updatedOrder);
    }
    
    /**
     * Delete order
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long orderId) {
        log.info("DELETE /orders/{} - Deleting order", orderId);
        orderService.deleteOrder(orderId);
        return ResponseEntity.noContent().build();
    }
}
