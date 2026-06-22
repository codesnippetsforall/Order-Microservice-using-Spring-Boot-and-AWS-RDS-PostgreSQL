package com.winsoon.orderms.controller;

import com.winsoon.orderms.dto.OrderItemDTO;
import com.winsoon.orderms.service.OrderItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OrderItem Controller
 * REST API endpoints for order item management
 */
@Slf4j
@RestController
@RequestMapping("/orders/{orderId}/items")
@CrossOrigin(origins = "*", maxAge = 3600)
public class OrderItemController {
    
    @Autowired
    private OrderItemService orderItemService;
    
    /**
     * Add item to order
     */
    @PostMapping
    public ResponseEntity<OrderItemDTO> addItemToOrder(
            @PathVariable Long orderId,
            @RequestBody OrderItemDTO itemDTO) {
        log.info("POST /orders/{}/items - Adding item to order", orderId);
        OrderItemDTO createdItem = orderItemService.addItemToOrder(orderId, itemDTO);
        return new ResponseEntity<>(createdItem, HttpStatus.CREATED);
    }
    
    /**
     * Get items by order ID
     */
    @GetMapping
    public ResponseEntity<List<OrderItemDTO>> getItemsByOrderId(@PathVariable Long orderId) {
        log.info("GET /orders/{}/items - Fetching items for order", orderId);
        List<OrderItemDTO> items = orderItemService.getItemsByOrderId(orderId);
        return ResponseEntity.ok(items);
    }
    
    /**
     * Get item by ID
     */
    @GetMapping("/{itemId}")
    public ResponseEntity<OrderItemDTO> getItemById(
            @PathVariable Long orderId,
            @PathVariable Long itemId) {
        log.info("GET /orders/{}/items/{} - Fetching item", orderId, itemId);
        OrderItemDTO item = orderItemService.getItemById(itemId);
        return ResponseEntity.ok(item);
    }
    
    /**
     * Update order item
     */
    @PutMapping("/{itemId}")
    public ResponseEntity<OrderItemDTO> updateOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody OrderItemDTO itemDTO) {
        log.info("PUT /orders/{}/items/{} - Updating item", orderId, itemId);
        OrderItemDTO updatedItem = orderItemService.updateOrderItem(itemId, itemDTO);
        return ResponseEntity.ok(updatedItem);
    }
    
    /**
     * Delete order item
     */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteOrderItem(
            @PathVariable Long orderId,
            @PathVariable Long itemId) {
        log.info("DELETE /orders/{}/items/{} - Deleting item", orderId, itemId);
        orderItemService.deleteOrderItem(itemId);
        return ResponseEntity.noContent().build();
    }
}
