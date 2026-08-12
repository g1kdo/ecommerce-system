-- ============================================================
-- Smart E-Commerce System — schema.sql
-- 3NF Relational Design (PostgreSQL syntax)
-- ============================================================

-- ---------- USERS ----------
CREATE TABLE Users (
                       user_id       SERIAL PRIMARY KEY,
                       username      VARCHAR(50)  NOT NULL UNIQUE,
                       email         VARCHAR(120) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       full_name     VARCHAR(120) NOT NULL,
                       phone         VARCHAR(20),
                       created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- CATEGORIES ----------
CREATE TABLE Categories (
                            category_id   SERIAL PRIMARY KEY,
                            name          VARCHAR(80) NOT NULL UNIQUE,
                            description   VARCHAR(255)
);

-- ---------- PRODUCTS ----------
CREATE TABLE Products (
                          product_id    SERIAL PRIMARY KEY,
                          name          VARCHAR(150) NOT NULL,
                          description   TEXT,
                          price         NUMERIC(12,2) NOT NULL CHECK (price >= 0),
                          sku           VARCHAR(40) NOT NULL UNIQUE,
                          category_id   INT NOT NULL REFERENCES Categories(category_id) ON DELETE RESTRICT,
                          created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- INVENTORY ----------
-- Split from Products (1:1) — stock quantity changes far more often than
-- product metadata; keeping it separate avoids unnecessary locking/rewrites
-- of descriptive columns during frequent stock updates.
CREATE TABLE Inventory (
                           inventory_id  SERIAL PRIMARY KEY,
                           product_id    INT NOT NULL UNIQUE REFERENCES Products(product_id) ON DELETE CASCADE,
                           quantity      INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
                           last_updated  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------- ORDERS ----------
CREATE TABLE Orders (
                        order_id      SERIAL PRIMARY KEY,
                        user_id       INT NOT NULL REFERENCES Users(user_id) ON DELETE RESTRICT,
                        order_date    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','PAID','SHIPPED','DELIVERED','CANCELLED')),
    -- Derived/cached total, maintained by the Service layer on every
    -- OrderItem write. Deliberate denormalization for read performance
    -- (dashboard/order-history queries read this far more than items change).
                        total_amount  NUMERIC(12,2) NOT NULL DEFAULT 0
);

-- ---------- ORDER ITEMS ----------
CREATE TABLE OrderItems (
                            order_item_id SERIAL PRIMARY KEY,
                            order_id      INT NOT NULL REFERENCES Orders(order_id) ON DELETE CASCADE,
                            product_id    INT NOT NULL REFERENCES Products(product_id) ON DELETE RESTRICT,
                            quantity      INT NOT NULL CHECK (quantity > 0),
    -- Snapshot of price at time of purchase (NOT looked up from Products at
    -- read time) — preserves historical accuracy of past orders.
                            unit_price    NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);

-- ---------- REVIEWS (thin relational shell; full content lives in NoSQL) ----------
CREATE TABLE Reviews (
                         review_id     SERIAL PRIMARY KEY,
                         product_id    INT NOT NULL REFERENCES Products(product_id) ON DELETE CASCADE,
                         user_id       INT NOT NULL REFERENCES Users(user_id) ON DELETE CASCADE,
                         rating        SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                         created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         UNIQUE (product_id, user_id) -- one rating per user per product
);

-- ============================================================
-- INDEXING STRATEGY
-- ============================================================

-- Case-insensitive product search (Epic 3 requirement)
CREATE INDEX idx_products_name_lower ON Products (LOWER(name));

-- Product listing filtered/joined by category
CREATE INDEX idx_products_category ON Products (category_id);

-- "My Orders" per-user lookup
CREATE INDEX idx_orders_user ON Orders (user_id);

-- Order detail joins (both directions)
CREATE INDEX idx_orderitems_order ON OrderItems (order_id);
CREATE INDEX idx_orderitems_product ON OrderItems (product_id);

-- Product detail page -> reviews join
CREATE INDEX idx_reviews_product ON Reviews (product_id);

-- Login lookup (email is already UNIQUE, which creates an index automatically,
-- listed here for documentation completeness)
-- UNIQUE index on Users(email) created implicitly by the UNIQUE constraint above.
