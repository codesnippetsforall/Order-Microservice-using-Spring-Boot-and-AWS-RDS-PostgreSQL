package com.winsoon.orderms.config;

import com.winsoon.orderms.entity.Customer;
import com.winsoon.orderms.entity.Order;
import com.winsoon.orderms.entity.OrderItem;
import com.winsoon.orderms.entity.OrderStatus;
import com.winsoon.orderms.repository.CustomerRepository;
import com.winsoon.orderms.repository.OrderItemRepository;
import com.winsoon.orderms.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * DataSeeder
 * Populates the database with sample customers, orders and order items
 * on application startup. Idempotent: it only seeds when the database is
 * empty, so restarts (with ddl-auto: update) will not duplicate rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public void run(String... args) {
        long existing = customerRepository.count();
        if (existing > 0) {
            log.info("Sample data already present ({} customers). Skipping seed.", existing);
            return;
        }

        log.info("No data found. Seeding sample customers, orders and order items...");

        // --- Customers ---
        Customer alice = customerRepository.save(Customer.builder()
                .firstName("Alice").lastName("Johnson")
                .email("alice.johnson@example.com").phoneNumber("+1-202-555-0143")
                .address("742 Evergreen Terrace").city("Springfield")
                .state("IL").postalCode("62704").country("USA")
                .build());

        Customer bob = customerRepository.save(Customer.builder()
                .firstName("Bob").lastName("Smith")
                .email("bob.smith@example.com").phoneNumber("+1-415-555-0178")
                .address("1600 Amphitheatre Pkwy").city("Mountain View")
                .state("CA").postalCode("94043").country("USA")
                .build());

        Customer chitra = customerRepository.save(Customer.builder()
                .firstName("Chitra").lastName("Raman")
                .email("chitra.raman@example.com").phoneNumber("+91-44-5550-1290")
                .address("12 Mount Road").city("Chennai")
                .state("TN").postalCode("600002").country("India")
                .build());

        // --- Orders + items for Alice ---
        Order aliceOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-1001")
                .customerId(alice.getCustomerId())
                .totalAmount(new BigDecimal("129.97"))
                .status(OrderStatus.CONFIRMED)
                .shippingAddress("742 Evergreen Terrace, Springfield, IL 62704")
                .build());

        orderItemRepository.save(OrderItem.builder()
                .order(aliceOrder).productId(5001L).productName("Wireless Mouse")
                .quantity(2).unitPrice(new BigDecimal("29.99"))
                .build());
        orderItemRepository.save(OrderItem.builder()
                .order(aliceOrder).productId(5002L).productName("Mechanical Keyboard")
                .quantity(1).unitPrice(new BigDecimal("69.99"))
                .build());

        // --- Order + item for Bob ---
        Order bobOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-1002")
                .customerId(bob.getCustomerId())
                .totalAmount(new BigDecimal("1199.00"))
                .status(OrderStatus.SHIPPED)
                .shippingAddress("1600 Amphitheatre Pkwy, Mountain View, CA 94043")
                .build());

        orderItemRepository.save(OrderItem.builder()
                .order(bobOrder).productId(5010L).productName("27\" 4K Monitor")
                .quantity(1).unitPrice(new BigDecimal("1199.00"))
                .build());

        // --- Pending order for Chitra ---
        Order chitraOrder = orderRepository.save(Order.builder()
                .orderNumber("ORD-1003")
                .customerId(chitra.getCustomerId())
                .totalAmount(new BigDecimal("45.50"))
                .status(OrderStatus.PENDING)
                .shippingAddress("12 Mount Road, Chennai, TN 600002")
                .build());

        orderItemRepository.save(OrderItem.builder()
                .order(chitraOrder).productId(5020L).productName("USB-C Cable (2m)")
                .quantity(5).unitPrice(new BigDecimal("9.10"))
                .build());

        log.info("Seed complete: {} customers, {} orders, {} order items.",
                customerRepository.count(), orderRepository.count(), orderItemRepository.count());
    }
}
