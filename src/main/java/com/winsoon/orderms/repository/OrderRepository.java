package com.winsoon.orderms.repository;

import com.winsoon.orderms.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Repository
 * Handles database operations for Order entity
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Optional<Order> findByOrderNumber(String orderNumber);
    
    List<Order> findByCustomerId(Long customerId);
    
    List<Order> findByStatus(String status);
    
    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.status = :status")
    List<Order> findByCustomerIdAndStatus(@Param("customerId") Long customerId, @Param("status") String status);
    
    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :startDate AND :endDate")
    List<Order> findOrdersBetweenDates(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate);
}
