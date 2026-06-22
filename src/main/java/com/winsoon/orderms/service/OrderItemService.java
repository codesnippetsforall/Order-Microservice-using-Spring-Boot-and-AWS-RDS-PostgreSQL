package com.winsoon.orderms.service;

import com.winsoon.orderms.dto.OrderItemDTO;
import com.winsoon.orderms.entity.Order;
import com.winsoon.orderms.entity.OrderItem;
import com.winsoon.orderms.repository.OrderItemRepository;
import com.winsoon.orderms.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OrderItem Service
 * Business logic for order item management
 */
@Slf4j
@Service
@Transactional
public class OrderItemService {
    
    @Autowired
    private OrderItemRepository orderItemRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    /**
     * Add item to order
     */
    public OrderItemDTO addItemToOrder(Long orderId, OrderItemDTO itemDTO) {
        log.info("Adding item to order ID: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));
        
        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .productId(itemDTO.getProductId())
                .productName(itemDTO.getProductName())
                .quantity(itemDTO.getQuantity())
                .unitPrice(itemDTO.getUnitPrice())
                .build();
        
        OrderItem savedItem = orderItemRepository.save(orderItem);
        log.info("Item added to order successfully");
        
        return convertToDTO(savedItem);
    }
    
    /**
     * Get items by order ID
     */
    @Transactional(readOnly = true)
    public List<OrderItemDTO> getItemsByOrderId(Long orderId) {
        log.info("Fetching items for order ID: {}", orderId);
        
        return orderItemRepository.findByOrderOrderId(orderId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Get item by ID
     */
    @Transactional(readOnly = true)
    public OrderItemDTO getItemById(Long itemId) {
        log.info("Fetching item with ID: {}", itemId);
        
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found with ID: " + itemId));
        
        return convertToDTO(item);
    }
    
    /**
     * Update order item
     */
    public OrderItemDTO updateOrderItem(Long itemId, OrderItemDTO itemDTO) {
        log.info("Updating item with ID: {}", itemId);
        
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found with ID: " + itemId));
        
        item.setQuantity(itemDTO.getQuantity());
        item.setUnitPrice(itemDTO.getUnitPrice());
        
        OrderItem updatedItem = orderItemRepository.save(item);
        log.info("Item updated successfully");
        
        return convertToDTO(updatedItem);
    }
    
    /**
     * Delete order item
     */
    public void deleteOrderItem(Long itemId) {
        log.info("Deleting item with ID: {}", itemId);
        
        if (!orderItemRepository.existsById(itemId)) {
            throw new RuntimeException("Item not found with ID: " + itemId);
        }
        
        orderItemRepository.deleteById(itemId);
        log.info("Item deleted successfully");
    }
    
    /**
     * Convert OrderItem entity to OrderItemDTO
     */
    private OrderItemDTO convertToDTO(OrderItem item) {
        return OrderItemDTO.builder()
                .orderItemId(item.getOrderItemId())
                .orderId(item.getOrder().getOrderId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
