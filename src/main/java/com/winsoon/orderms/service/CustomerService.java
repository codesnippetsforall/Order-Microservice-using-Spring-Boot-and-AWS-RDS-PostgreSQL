package com.winsoon.orderms.service;

import com.winsoon.orderms.config.CacheNames;
import com.winsoon.orderms.dto.CustomerDTO;
import com.winsoon.orderms.entity.Customer;
import com.winsoon.orderms.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer Service
 * Business logic for customer management
 */
@Slf4j
@Service
@Transactional
public class CustomerService {
    
    @Autowired
    private CustomerRepository customerRepository;
    
    /**
     * Create a new customer
     */
    @CacheEvict(cacheNames = CacheNames.CUSTOMERS, allEntries = true)
    public CustomerDTO createCustomer(CustomerDTO customerDTO) {
        log.info("Creating new customer with email: {}", customerDTO.getEmail());
        
        Customer customer = Customer.builder()
                .firstName(customerDTO.getFirstName())
                .lastName(customerDTO.getLastName())
                .email(customerDTO.getEmail())
                .phoneNumber(customerDTO.getPhoneNumber())
                .address(customerDTO.getAddress())
                .city(customerDTO.getCity())
                .state(customerDTO.getState())
                .postalCode(customerDTO.getPostalCode())
                .country(customerDTO.getCountry())
                .build();
        
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created successfully with ID: {}", savedCustomer.getCustomerId());
        
        return convertToDTO(savedCustomer);
    }
    
    /**
     * Get customer by ID
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CUSTOMERS, key = "#customerId")
    public CustomerDTO getCustomerById(Long customerId) {
        log.info("Fetching customer with ID: {}", customerId);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + customerId));
        
        return convertToDTO(customer);
    }
    
    /**
     * Get customer by email
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CUSTOMERS, key = "'email:' + #email")
    public CustomerDTO getCustomerByEmail(String email) {
        log.info("Fetching customer with email: {}", email);
        
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Customer not found with email: " + email));
        
        return convertToDTO(customer);
    }
    
    /**
     * Get all customers
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.CUSTOMERS, key = "'all'")
    public List<CustomerDTO> getAllCustomers() {
        log.info("Fetching all customers");
        
        return customerRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Update customer
     */
    @CacheEvict(cacheNames = CacheNames.CUSTOMERS, allEntries = true)
    public CustomerDTO updateCustomer(Long customerId, CustomerDTO customerDTO) {
        log.info("Updating customer with ID: {}", customerId);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with ID: " + customerId));
        
        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setPhoneNumber(customerDTO.getPhoneNumber());
        customer.setAddress(customerDTO.getAddress());
        customer.setCity(customerDTO.getCity());
        customer.setState(customerDTO.getState());
        customer.setPostalCode(customerDTO.getPostalCode());
        customer.setCountry(customerDTO.getCountry());
        
        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer updated successfully");
        
        return convertToDTO(updatedCustomer);
    }
    
    /**
     * Delete customer
     */
    @CacheEvict(cacheNames = CacheNames.CUSTOMERS, allEntries = true)
    public void deleteCustomer(Long customerId) {
        log.info("Deleting customer with ID: {}", customerId);
        
        if (!customerRepository.existsById(customerId)) {
            throw new RuntimeException("Customer not found with ID: " + customerId);
        }
        
        customerRepository.deleteById(customerId);
        log.info("Customer deleted successfully");
    }
    
    /**
     * Convert Customer entity to CustomerDTO
     */
    private CustomerDTO convertToDTO(Customer customer) {
        return CustomerDTO.builder()
                .customerId(customer.getCustomerId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .address(customer.getAddress())
                .city(customer.getCity())
                .state(customer.getState())
                .postalCode(customer.getPostalCode())
                .country(customer.getCountry())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
