-- Flyway Migration: V2__Insert_Sample_Data.sql
-- This script inserts sample data into the database

-- Insert sample customers
INSERT INTO customers (first_name, last_name, email, phone_number, address, city, state, postal_code, country, created_at, updated_at) 
VALUES 
('John', 'Doe', 'john.doe@example.com', '+91-9876543210', '123 Main St', 'Hyderabad', 'Telangana', '500001', 'India', NOW(), NOW()),
('Jane', 'Smith', 'jane.smith@example.com', '+91-9876543211', '456 Oak Ave', 'Bangalore', 'Karnataka', '560001', 'India', NOW(), NOW()),
('Robert', 'Johnson', 'robert.johnson@example.com', '+91-9876543212', '789 Pine Rd', 'Chennai', 'Tamil Nadu', '600001', 'India', NOW(), NOW()),
('Emily', 'Williams', 'emily.williams@example.com', '+91-9876543213', '321 Elm St', 'Mumbai', 'Maharashtra', '400001', 'India', NOW(), NOW()),
('Michael', 'Brown', 'michael.brown@example.com', '+91-9876543214', '654 Maple Dr', 'Delhi', 'Delhi', '110001', 'India', NOW(), NOW());

-- Insert sample orders
INSERT INTO orders (order_number, customer_id, order_date, total_amount, status, shipping_address, created_at, updated_at)
VALUES
('ORD-20240101001', 1, NOW() - INTERVAL '5 days', 1500.00, 'DELIVERED', '123 Main St, Hyderabad', NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 day'),
('ORD-20240102002', 2, NOW() - INTERVAL '3 days', 2500.00, 'SHIPPED', '456 Oak Ave, Bangalore', NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day'),
('ORD-20240103003', 3, NOW() - INTERVAL '2 days', 3000.00, 'CONFIRMED', '789 Pine Rd, Chennai', NOW() - INTERVAL '2 days', NOW()),
('ORD-20240104004', 4, NOW() - INTERVAL '1 day', 1800.00, 'PENDING', '321 Elm St, Mumbai', NOW() - INTERVAL '1 day', NOW()),
('ORD-20240105005', 5, NOW(), 2200.00, 'PENDING', '654 Maple Dr, Delhi', NOW(), NOW());

-- Insert sample order items
INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, total_price, created_at)
VALUES
(1, 101, 'Laptop', 1, 1000.00, 1000.00, NOW() - INTERVAL '5 days'),
(1, 102, 'Mouse', 1, 500.00, 500.00, NOW() - INTERVAL '5 days'),
(2, 103, 'Monitor', 2, 1000.00, 2000.00, NOW() - INTERVAL '3 days'),
(2, 104, 'Keyboard', 1, 500.00, 500.00, NOW() - INTERVAL '3 days'),
(3, 105, 'Headphones', 1, 3000.00, 3000.00, NOW() - INTERVAL '2 days'),
(4, 106, 'USB Cable', 2, 500.00, 1000.00, NOW() - INTERVAL '1 day'),
(4, 107, 'Phone Stand', 1, 800.00, 800.00, NOW() - INTERVAL '1 day'),
(5, 108, 'Webcam', 1, 2200.00, 2200.00, NOW());

COMMIT;
