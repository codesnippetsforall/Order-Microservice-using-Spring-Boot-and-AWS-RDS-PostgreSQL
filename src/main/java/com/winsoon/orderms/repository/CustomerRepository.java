package com.winsoon.orderms.repository;

import com.winsoon.orderms.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Customer Repository
 * Handles database operations for Customer entity
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    Optional<Customer> findByEmail(String email);
    
    Optional<Customer> findByPhoneNumber(String phoneNumber);
}
