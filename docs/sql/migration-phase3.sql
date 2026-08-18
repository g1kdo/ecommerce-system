-- ============================================================
-- Smart E-Commerce System — Phase 2 -> Phase 3 migration
-- ============================================================
-- Adds the checkout-shortfall audit table and the indexes the Phase 3 queries
-- need. Three of these indexes close recommendations left open by
-- docs/performance-report.md §8.2 rather than being new ideas.
--
-- Run once against each existing database, BEFORE starting the Phase 3 app:
--   psql -h localhost -U postgres -d smart_ecommerce_db -f docs/sql/migration-phase3.sql
--
-- Idempotent: safe to run more than once.
--
-- Note on ddl-auto=update: it will create checkout_shortfalls for you in dev,
-- but it will not create any of the indexes below, and prod runs `validate`.
-- This file is the authority for both.

BEGIN;

-- ------------------------------------------------------------
-- 1. Checkout shortfall audit
-- ------------------------------------------------------------
-- Written by CheckoutAuditServiceImpl on a REQUIRES_NEW transaction, so the row
-- survives the rollback of the checkout that produced it.
--
-- There are deliberately NO foreign keys to users or products. An audit row
-- states what was true at a moment in time: it must not block a product from
-- being deleted later, and it must not vanish when one is. The columns are
-- plain ids, and the report that reads them tolerates an id with no row behind
-- it — that is the correct outcome, not a dangling reference.

CREATE TABLE IF NOT EXISTS checkout_shortfalls (
    shortfall_id       BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL,
    product_id         BIGINT      NOT NULL,
    requested_quantity INTEGER     NOT NULL CHECK (requested_quantity > 0),
    available_quantity INTEGER     NOT NULL CHECK (available_quantity >= 0),
    recorded_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- summarizeMissedDemand groups by product over a recent window; the report
-- endpoint and the per-user lookup take the other two.
CREATE INDEX IF NOT EXISTS idx_shortfalls_product  ON checkout_shortfalls (product_id);
CREATE INDEX IF NOT EXISTS idx_shortfalls_recorded ON checkout_shortfalls (recorded_at);
CREATE INDEX IF NOT EXISTS idx_shortfalls_user     ON checkout_shortfalls (user_id, recorded_at DESC);

-- ------------------------------------------------------------
-- 2. Indexes for the Phase 3 reporting queries
-- ------------------------------------------------------------

-- Every report is bounded by a date window: daily sales, revenue, the status
-- breakdown, findByOrderDateBetween. Phase 2 indexed user_id and status but
-- never order_date, so all of them scan.
CREATE INDEX IF NOT EXISTS idx_orders_order_date ON orders (order_date);

-- summarizeByStatus filters on the window and groups by status. Leading with
-- status lets the group be read in order; leading with order_date would need a
-- sort on top of the range scan.
CREATE INDEX IF NOT EXISTS idx_orders_status_date ON orders (status, order_date);

-- findByUserIdAndStatus. idx_orders_user alone leaves the status as a filter
-- applied after the fetch.
CREATE INDEX IF NOT EXISTS idx_orders_user_status ON orders (user_id, status);

-- findLowStock scans inventory for quantity <= threshold. A partial index is
-- tempting and wrong here: the threshold is a request parameter, so an index
-- built for one value cannot serve another.
CREATE INDEX IF NOT EXISTS idx_inventory_quantity ON inventory (quantity);

-- findByCreatedAtAfter, the new-registrations report.
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

-- findBoughtTogetherWith self-joins order_items on order_id after filtering on
-- product_id. The existing single-column indexes make it two separate scans.
CREATE INDEX IF NOT EXISTS idx_order_items_product_order ON order_items (product_id, order_id);

-- ------------------------------------------------------------
-- 3. Recommendations carried over from the Phase 1 index study
-- ------------------------------------------------------------
-- docs/performance-report.md §8.2 left these open with measurements attached.
-- They are applied here because Phase 3 made the queries behind them hotter.

-- §8.2 #5, measured at Q7: the category listing was only 4.1x faster with a
-- category_id index because ORDER BY name still had to sort 10 000 rows. The
-- composite lets the sort be skipped entirely.
CREATE INDEX IF NOT EXISTS idx_products_category_name ON products (category_id, LOWER(name));

-- §8.2 #2, measured at Q4: 109.4 ms -> 11.2 ms, 9.7x. ProductSpecifications
-- builds a leading-wildcard LIKE ('%mouse%'), which no B-tree can serve — not
-- even the text_pattern_ops variant. A trigram GIN index is the only structure
-- that answers it.
--
-- This is the most expensive index in the schema to build and to maintain
-- (~3 s build, and a write cost on every product insert). It earns that on a
-- catalogue where search is the primary way in; drop it if it is not.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_products_name_trgm ON products USING GIN (LOWER(name) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_products_sku_trgm  ON products USING GIN (LOWER(sku) gin_trgm_ops);

COMMIT;

-- ------------------------------------------------------------
-- 4. After running
-- ------------------------------------------------------------
-- The planner will not use a new index until it has statistics for it.

ANALYZE orders;
ANALYZE order_items;
ANALYZE products;
ANALYZE inventory;
ANALYZE users;
ANALYZE checkout_shortfalls;
