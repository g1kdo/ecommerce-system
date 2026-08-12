-- ============================================================
-- Smart E-Commerce System — Phase 1 -> Phase 2 migration
-- ============================================================
-- Brings a database created from docs/sql/schema.sql in line with the JPA
-- entities introduced in Phase 2.
--
-- Why this exists at all: `spring.jpa.hibernate.ddl-auto=update` cannot perform
-- either change below. It refuses to add a NOT NULL column to a table that
-- already holds rows, and it has no notion of renaming a table — it would
-- simply create an empty one alongside the old, stranding the data.
--
-- Run once against each existing database, BEFORE starting the Phase 2 app:
--   psql -h localhost -U postgres -d smart_ecommerce_db -f docs/sql/migration-phase2.sql
--
-- Idempotent: safe to run more than once.

BEGIN;

-- ------------------------------------------------------------
-- 1. Users gain a role (ADMIN / CUSTOMER)
-- ------------------------------------------------------------
-- Added nullable first, backfilled, then constrained — the only order that
-- works on a table with existing rows.

ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20);

-- Existing accounts predate the role split; CUSTOMER is the safe default,
-- because granting administrator rights by accident is the costlier mistake.
UPDATE users SET role = 'CUSTOMER' WHERE role IS NULL;

ALTER TABLE users ALTER COLUMN role SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_users_role') THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'CUSTOMER'));
    END IF;
END $$;

-- Promote the first account to administrator so the admin API is reachable.
-- Adjust the e-mail to whichever account should hold the role.
UPDATE users SET role = 'ADMIN' WHERE user_id = (SELECT MIN(user_id) FROM users);

-- ------------------------------------------------------------
-- 2. OrderItems -> order_items
-- ------------------------------------------------------------
-- Phase 1 created "OrderItems", which PostgreSQL folded to `orderitems`. The
-- Phase 2 entity maps to `order_items`, so any rows written before the
-- migration have to be carried across explicitly.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = 'public' AND table_name = 'orderitems') THEN

        -- Hibernate may already have created an empty order_items; if it has not,
        -- rename in place and keep the original identifiers.
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = 'public' AND table_name = 'order_items') THEN
            ALTER TABLE orderitems RENAME TO order_items;
        ELSE
            INSERT INTO order_items (order_item_id, order_id, product_id, quantity, unit_price)
            SELECT order_item_id, order_id, product_id, quantity, unit_price
            FROM orderitems
            WHERE order_item_id NOT IN (SELECT order_item_id FROM order_items);

            -- Keep the identity sequence ahead of the migrated rows, otherwise the
            -- next insert collides with a primary key that already exists.
            PERFORM setval(
                pg_get_serial_sequence('order_items', 'order_item_id'),
                GREATEST((SELECT COALESCE(MAX(order_item_id), 1) FROM order_items), 1));

            DROP TABLE orderitems;
        END IF;
    END IF;
END $$;

-- ------------------------------------------------------------
-- 3. Indexes for the Phase 2 access patterns
-- ------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_products_name_lower ON products (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_products_category   ON products (category_id);
CREATE INDEX IF NOT EXISTS idx_products_price      ON products (price);
CREATE INDEX IF NOT EXISTS idx_orders_user         ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status       ON orders (status);
CREATE INDEX IF NOT EXISTS idx_order_items_order   ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product ON order_items (product_id);
CREATE INDEX IF NOT EXISTS idx_users_role          ON users (role);

COMMIT;

-- ------------------------------------------------------------
-- 4. Reviews
-- ------------------------------------------------------------
-- The relational `reviews` table is superseded by the MongoDB `reviews`
-- collection in Phase 2 and is no longer read by the application. It is left in
-- place deliberately — dropping it would destroy Phase 1 data that has not been
-- migrated. Copy it across, then drop it manually when you are satisfied:
--
--   DROP TABLE reviews;
