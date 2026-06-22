package com.winsoon.orderms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Order Microservice Application
 * 
 * This is the main entry point for the Spring Boot Order Microservice application.
 * It connects to AWS RDS PostgreSQL database for order management.
 */
@SpringBootApplication
public class OrderMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderMicroserviceApplication.class, args);
    }
}
