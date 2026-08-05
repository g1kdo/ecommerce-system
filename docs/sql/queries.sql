-- ============================================================
-- Smart E-Commerce System — queries.sql
-- Sample DML + performance benchmark queries
-- ============================================================

-- ------------------------------------------------------------
-- SAMPLE SEED DATA
-- ------------------------------------------------------------
INSERT INTO Categories (name, description) VALUES
  ('Electronics', 'Devices and gadgets'),
  ('Home & Kitchen', 'Household items'),
  ('Books', 'Printed and digital books');

INSERT INTO Products (name, description, price, sku, category_id) VALUES
  ('Wireless Mouse', 'Ergonomic 2.4GHz wireless mouse', 19.99, 'SKU-1001', 1),
  ('Mechanical Keyboard', 'RGB backlit mechanical keyboard', 59.99, 'SKU-1002', 1),
  ('Stainless Steel Pan', '12-inch non-stick pan', 34.50, 'SKU-2001', 2);

INSERT INTO Inventory (product_id, quantity) VALUES
  (1, 150), (2, 80), (3, 45);

INSERT INTO Users (username, email, password_hash, full_name) VALUES
  ('jdoe', 'jdoe@example.com', 'HASHED_PW', 'John Doe');

-- ------------------------------------------------------------
-- PARAMETERIZED QUERY PATTERNS
-- (executed via PreparedStatement in the DAO layer — never string-concatenated)
-- ------------------------------------------------------------

-- Case-insensitive product search
-- PreparedStatement: "SELECT * FROM Products WHERE LOWER(name) LIKE ?"
-- bound param: "%" + searchTerm.toLowerCase() + "%"

-- Products by category (paginated)
-- PreparedStatement: "SELECT * FROM Products WHERE category_id = ? ORDER BY name LIMIT ? OFFSET ?"

-- Insert a new order + items (transactional, done in OrderDAO)
-- PreparedStatement: "INSERT INTO Orders (user_id, status, total_amount) VALUES (?, ?, ?)"
-- PreparedStatement: "INSERT INTO OrderItems (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)"

-- Update inventory after a sale
-- PreparedStatement: "UPDATE Inventory SET quantity = quantity - ?, last_updated = CURRENT_TIMESTAMP WHERE product_id = ?"

-- Average rating for a product
-- PreparedStatement: "SELECT AVG(rating) FROM Reviews WHERE product_id = ?"

-- ------------------------------------------------------------
-- PERFORMANCE BENCHMARKS: BEFORE vs AFTER INDEXING
-- ------------------------------------------------------------

-- STEP 1: Drop the search index to measure the "before" baseline
DROP INDEX IF EXISTS idx_products_name_lower;

-- "Before" timing (run in psql with \timing on, or wrap in application code)
EXPLAIN ANALYZE
SELECT * FROM Products WHERE LOWER(name) LIKE '%mouse%';
-- Expected plan: Seq Scan on Products (cost grows linearly with table size)

-- STEP 2: Recreate the functional index
CREATE INDEX idx_products_name_lower ON Products (LOWER(name));

-- "After" timing
EXPLAIN ANALYZE
SELECT * FROM Products WHERE LOWER(name) LIKE '%mouse%';
-- Expected plan: Bitmap/Index Scan using idx_products_name_lower

-- Same before/after pattern applied to the Orders(user_id) lookup:
-- DROP INDEX idx_orders_user;
EXPLAIN ANALYZE SELECT * FROM Orders WHERE user_id = 1;
-- CREATE INDEX idx_orders_user ON Orders(user_id);
EXPLAIN ANALYZE SELECT * FROM Orders WHERE user_id = 1;

-- Document actual millisecond timings captured from EXPLAIN ANALYZE output
-- in README.md's "Performance Comparison" section.
