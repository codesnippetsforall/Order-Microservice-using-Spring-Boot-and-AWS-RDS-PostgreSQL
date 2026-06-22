package com.winsoon.orderms.entity;

/**
 * OrderStatus
 * Lifecycle states of an order.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
