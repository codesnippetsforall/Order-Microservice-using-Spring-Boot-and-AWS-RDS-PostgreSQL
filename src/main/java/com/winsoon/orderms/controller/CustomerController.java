package com.winsoon.orderms.controller;

import com.winsoon.orderms.dto.CustomerDTO;
import com.winsoon.orderms.service.CustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer Controller
 * REST API endpoints for customer management
 */
@Slf4j
@RestController
@RequestMapping("/customers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CustomerController {
    
    @Autowired
    private CustomerService customerService;
    
    /**
     * Create a new customer
     */
    @PostMapping
    public ResponseEntity<CustomerDTO> createCustomer(@RequestBody CustomerDTO customerDTO) {
        log.info("POST /customers - Creating new customer");
        CustomerDTO createdCustomer = customerService.createCustomer(customerDTO);
        return new ResponseEntity<>(createdCustomer, HttpStatus.CREATED);
    }
    
    /**
     * Get customer by ID
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerDTO> getCustomerById(@PathVariable Long customerId) {
        log.info("GET /customers/{} - Fetching customer", customerId);
        CustomerDTO customer = customerService.getCustomerById(customerId);
        return ResponseEntity.ok(customer);
    }
    
    /**
     * Get customer by email
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<CustomerDTO> getCustomerByEmail(@PathVariable String email) {
        log.info("GET /customers/email/{} - Fetching customer by email", email);
        CustomerDTO customer = customerService.getCustomerByEmail(email);
        return ResponseEntity.ok(customer);
    }
    
    /**
     * Get all customers
     */
    @GetMapping
    public ResponseEntity<List<CustomerDTO>> getAllCustomers() {
        log.info("GET /customers - Fetching all customers");
        List<CustomerDTO> customers = customerService.getAllCustomers();
        return ResponseEntity.ok(customers);
    }
    
    /**
     * Update customer
     */
    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerDTO> updateCustomer(
            @PathVariable Long customerId,
            @RequestBody CustomerDTO customerDTO) {
        log.info("PUT /customers/{} - Updating customer", customerId);
        CustomerDTO updatedCustomer = customerService.updateCustomer(customerId, customerDTO);
        return ResponseEntity.ok(updatedCustomer);
    }
    
    /**
     * Delete customer
     */
    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long customerId) {
        log.info("DELETE /customers/{} - Deleting customer", customerId);
        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}
