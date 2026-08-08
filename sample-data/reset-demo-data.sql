-- Resets the Railway demo database to a clean, curated state.
--
-- Runs exclusively as the `investlog-demo-reset` Railway service (deploy.cronSchedule,
-- 3am UTC daily) — no application code in server/ triggers or depends on this. Not intended
-- for local dev or prod: it deletes every non-admin account and reloads admin's sample data
-- from scratch. admin@admin.com's login credentials and non-financial profile fields (name,
-- accent color, preferred currency, avatar) are untouched — only financial/demo data resets.
--
-- Order matters: wallets must go first (cascades to every holding/lot/contribution, admin's
-- included), so stock_types/fund_types have no remaining holding references and non-admin
-- users have no remaining wallet reference (finances.wallets.user_id has no ON DELETE CASCADE).
--
-- Wrapped in one transaction (with ON_ERROR_STOP=1 in the invoking psql call, see
-- Dockerfile.reset) so a failure at any step — including inside sample-data.sql — rolls back
-- the whole reset instead of leaving the demo wiped but not reloaded.

BEGIN;

-- 1. Wipe every wallet (and, via ON DELETE CASCADE, every holding/lot/contribution) for every
--    user, including admin.
DELETE FROM finances.wallets;

-- 2. Global type lists (finances.stock_types/fund_types have carried no user_id since
--    24-1200-make-settings-global.xml) — safe now that no holding references them.
DELETE FROM finances.stock_types;
DELETE FROM finances.fund_types;

-- 3. Global currency rates, reseeded to the same defaults Liquibase seeds on a fresh install
--    (24-1200-make-settings-global.xml, changeset 24-1200-9).
DELETE FROM finances.currency_rates;
INSERT INTO finances.currency_rates (currency_code, rate, is_base)
VALUES ('BRL', 1, true), ('USD', 5, false);

-- 4. Every non-admin account. Safe now that all non-admin wallets are already gone —
--    deleting a user with a remaining wallet would otherwise violate wallets.user_id's FK.
DELETE FROM system.users WHERE email <> 'admin@admin.com';

-- 5. Recreate admin's three sample wallets, holdings, and lots. \ir resolves relative to this
--    script's own location rather than psql's working directory.
\ir sample-data.sql

COMMIT;
