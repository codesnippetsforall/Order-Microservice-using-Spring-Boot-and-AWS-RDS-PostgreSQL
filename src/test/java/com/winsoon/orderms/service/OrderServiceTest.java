package com.winsoon.orderms.service;

import com.winsoon.orderms.dto.OrderDTO;
import com.winsoon.orderms.entity.Order;
import com.winsoon.orderms.entity.OrderStatus;
import com.winsoon.orderms.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for OrderService
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @InjectMocks
    private OrderService orderService;
    
    private Order mockOrder;
    private OrderDTO mockOrderDTO;
    
    @BeforeEach
    void setUp() {
        mockOrder = Order.builder()
                .orderId(1L)
                .orderNumber("ORD-123456")
                .customerId(1L)
                .totalAmount(new BigDecimal("5000.00"))
                .status(OrderStatus.PENDING)
                .shippingAddress("123 Main St")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        mockOrderDTO = OrderDTO.builder()
                .orderId(1L)
                .orderNumber("ORD-123456")
                .customerId(1L)
                .totalAmount(new BigDecimal("5000.00"))
                .status("PENDING")
                .shippingAddress("123 Main St")
                .build();
    }
    
    @Test
    void testGetOrderById() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(mockOrder));
        
        OrderDTO result = orderService.getOrderById(1L);
        
        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals("ORD-123456", result.getOrderNumber());
    }
    
    @Test
    void testGetOrderById_NotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        
        assertThrows(RuntimeException.class, () -> orderService.getOrderById(999L));
    }
    
    @Test
    void testCreateOrder() {
        when(orderRepository.save(any(Order.class))).thenReturn(mockOrder);
        
        OrderDTO result = orderService.createOrder(mockOrderDTO);
        
        assertNotNull(result);
        assertEquals("ORD", result.getOrderNumber().substring(0, 3));
    }
}
