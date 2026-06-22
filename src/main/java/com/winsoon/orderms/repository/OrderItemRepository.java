package com.winsoon.orderms.repository;

import com.winsoon.orderms.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * OrderItem Repository
 * Handles database operations for OrderItem entity
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    List<OrderItem> findByOrderOrderId(Long orderId);
    
    List<OrderItem> findByProductId(Long productId);
}
