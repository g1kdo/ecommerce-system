-- ============================================================
-- Smart E-Commerce System — benchmark_seed.sql
-- Generates a realistic-volume dataset for the Epic 4 performance
-- measurements in docs/performance-report.md.
--
-- Run against a THROWAWAY database, never the application database:
--   createdb smart_ecommerce_benchmark
--   psql -d smart_ecommerce_benchmark -f docs/sql/schema.sql
--   psql -d smart_ecommerce_benchmark -f docs/sql/benchmark_seed.sql
-- ============================================================

-- ---------- volumes ----------
--   categories     20
--   users          50,000
--   products      200,000
--   inventory     200,000
--   orders        200,000
--   order items   600,000
--   reviews       300,000

INSERT INTO Categories (name, description)
SELECT 'Category ' || i, 'Generated category ' || i
FROM generate_series(1, 20) AS i;

INSERT INTO Users (username, email, password_hash, full_name, phone)
SELECT 'user' || i,
       'user' || i || '@example.com',
       repeat('a', 64),                    -- placeholder digest, never authenticated against
       'Test User ' || i,
       '+25078' || lpad(i::text, 7, '0')
FROM generate_series(1, 50000) AS i;

-- Product names repeat a small vocabulary so text search has realistic selectivity:
-- roughly 1 in 10 products contains 'Mouse'.
INSERT INTO Products (name, description, price, sku, category_id)
SELECT (ARRAY['Wireless Mouse', 'Mechanical Keyboard', 'Stainless Steel Pan',
              'USB-C Hub', 'Noise Cancelling Headset', 'Standing Desk Mat',
              'Laptop Stand', 'Webcam 1080p', 'Desk Lamp', 'Cable Organizer'])[1 + (i % 10)]
           || ' Model ' || i,
       'Generated product ' || i,
       round((5 + (i % 500) + (i % 97) / 100.0)::numeric, 2),
       'SKU-' || lpad(i::text, 8, '0'),
       1 + (i % 20)
FROM generate_series(1, 200000) AS i;

INSERT INTO Inventory (product_id, quantity)
SELECT i, (i * 7) % 500
FROM generate_series(1, 200000) AS i;

-- ~4 orders per user, spread over the past year
INSERT INTO Orders (user_id, order_date, status, total_amount)
SELECT 1 + (i % 50000),
       CURRENT_TIMESTAMP - ((i % 365) || ' days')::interval,
       (ARRAY['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'])[1 + (i % 5)],
       round((10 + (i % 900))::numeric, 2)
FROM generate_series(1, 200000) AS i;

-- 3 lines per order
INSERT INTO OrderItems (order_id, product_id, quantity, unit_price)
SELECT 1 + (i % 200000),
       1 + ((i * 31) % 200000),
       1 + (i % 5),
       round((5 + (i % 400))::numeric, 2)
FROM generate_series(1, 600000) AS i;

-- UNIQUE (product_id, user_id) is respected: the product id cycles every 200k rows
-- while the user id advances, so no pair repeats.
INSERT INTO Reviews (product_id, user_id, rating, created_at)
SELECT 1 + ((i - 1) % 200000),
       1 + ((i - 1) / 200000),
       1 + (i % 5),
       CURRENT_TIMESTAMP - ((i % 200) || ' days')::interval
FROM generate_series(1, 300000) AS i;

-- Planner statistics must be current or the "after" plans are not trustworthy.
ANALYZE;
