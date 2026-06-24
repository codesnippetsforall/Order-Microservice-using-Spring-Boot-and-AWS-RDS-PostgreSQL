package com.winsoon.orderms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Order Microservice Application
 *
 * Connects to AWS RDS PostgreSQL and optional ElastiCache Redis (via Secrets Manager).
 */
@SpringBootApplication
@EnableCaching
public class OrderMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderMicroserviceApplication.class, args);
    }
}
