-- ============================================================
-- Smart E-Commerce System — password hash encoding migration
-- ============================================================
-- Phase 1 stored raw, unsalted SHA-256 hex. The application now uses a
-- DelegatingPasswordEncoder, which identifies an algorithm by a {prefix} on the
-- stored value and writes BCrypt for anything new.
--
-- This script tags the existing rows so they are read as SHA-256 instead of
-- being fed to BCrypt (which would reject every one of them and lock the
-- accounts out). Nothing is re-hashed here: the plaintext is not available, and
-- inventing one would be worse than leaving the row alone.
--
-- After this runs, each account upgrades itself. The legacy encoder reports its
-- hashes as outdated, so on the owner's next successful sign-in Spring Security
-- calls UserDetailsPasswordService.updatePassword and rewrites the row as
-- {bcrypt}. The SHA-256 rows disappear one login at a time, with no reset e-mail
-- and no downtime.
--
--   psql -h localhost -U postgres -d smart_ecommerce_db -f docs/sql/migration-password-encoding.sql
--
-- Idempotent: rows that already carry a {prefix} are left untouched.

BEGIN;

UPDATE users
SET password_hash = '{sha256}' || password_hash
WHERE password_hash NOT LIKE '{%}%';

COMMIT;

-- Progress check — how much of the estate is still on the legacy hash:
--
--   SELECT
--       COUNT(*) FILTER (WHERE password_hash LIKE '{sha256}%') AS legacy,
--       COUNT(*) FILTER (WHERE password_hash LIKE '{bcrypt}%') AS upgraded
--   FROM users;
--
-- Once `legacy` reaches zero, the {sha256} entry can be removed from
-- SecurityConfig.passwordEncoder() and LegacySha256PasswordEncoder deleted.
