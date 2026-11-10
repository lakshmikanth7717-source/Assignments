-- Repeatable, atomic seed for an empty migrated database. No history guards are disabled.
\timing on
BEGIN;
SELECT pg_advisory_xact_lock(77172000);
CREATE TABLE IF NOT EXISTS seed_batches (batch_name TEXT PRIMARY KEY, completed_at TIMESTAMPTZ NOT NULL);
SELECT NOT EXISTS (SELECT 1 FROM seed_batches WHERE batch_name = '1000-2000-100000-v1') AS run_seed \gset
\if :run_seed
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM wallets) OR EXISTS (SELECT 1 FROM transactions) THEN
        RAISE EXCEPTION 'Seed requires an empty financial database. Use a separate Compose project for demo data.';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE email <> 'admin@digi-wallet.local') THEN
        RAISE EXCEPTION 'Seed requires only the bootstrap admin user.';
    END IF;
END $$;

-- Include the API bootstrap administrator in the requested total of exactly 1,000 users.
INSERT INTO users(name, email, phone)
SELECT 'Seed User ' || n, 'seed-' || n || '@wallet.test', '9000000000'
FROM generate_series(1, 999) n;
CREATE TEMP TABLE seed_users AS
SELECT user_id, row_number() OVER (ORDER BY user_id) AS n FROM users;
INSERT INTO wallets(user_id, wallet_name)
SELECT user_id, 'Seed wallet ' || n || '-' || slot FROM seed_users CROSS JOIN generate_series(1, 2) slot;
CREATE TEMP TABLE seed_wallets AS
SELECT wallet_id, user_id, row_number() OVER (ORDER BY wallet_id) AS n FROM wallets;

-- Every opening balance is backed by a balanced cash-in transaction.
CREATE TEMP TABLE seed_topups AS
WITH headers AS (
    INSERT INTO transactions(transaction_type, transaction_amount_paise, wallet_id, initiated_by_user_id, account_id,
                             transaction_date)
    SELECT 'TOPUP', 1000000, wallet_id, user_id,
           (SELECT account_id FROM accounts WHERE account_type = 'CASH_IN'), now() - interval '2 days'
    FROM seed_wallets RETURNING transaction_id, wallet_id
) SELECT * FROM headers;
INSERT INTO transaction_status_history(transaction_id, status, occurred_at)
SELECT transaction_id, 'PENDING', now() - interval '2 days' FROM seed_topups;
INSERT INTO ledger_entries(transaction_id, wallet_id, amount_paise, created_at)
SELECT transaction_id, wallet_id, 1000000, now() - interval '2 days' FROM seed_topups;
INSERT INTO ledger_entries(transaction_id, system_account_id, amount_paise, created_at)
SELECT transaction_id, (SELECT system_account_id FROM system_accounts WHERE account_type = 'CASH_IN'),
       -1000000, now() - interval '2 days' FROM seed_topups;
UPDATE transactions SET transaction_status = 'POSTED', posted_at = now() - interval '2 days'
WHERE transaction_id IN (SELECT transaction_id FROM seed_topups);
INSERT INTO transaction_status_history(transaction_id, status, occurred_at)
SELECT transaction_id, 'POSTED', now() - interval '2 days' FROM seed_topups;

-- Fifty complete rings: each wallet sends and receives 50 x 100 paise.
CREATE TEMP TABLE seed_transfers AS
WITH headers AS (
    INSERT INTO transactions(transaction_type, transaction_amount_paise, wallet_id, initiated_by_user_id, account_id,
                             transaction_date)
    SELECT 'TRANSFER', 100, w.wallet_id, w.user_id,
           (SELECT account_id FROM accounts WHERE account_type = 'INTERNAL_TRANSFER'), now() - interval '1 day'
    FROM generate_series(1, 100000) g JOIN seed_wallets w ON w.n = ((g - 1) % 2000) + 1
    RETURNING transaction_id, wallet_id
) SELECT h.*, recipient.wallet_id AS recipient_id FROM headers h
  JOIN seed_wallets sender ON sender.wallet_id = h.wallet_id
  JOIN seed_wallets recipient ON recipient.n = (sender.n % 2000) + 1;
INSERT INTO transaction_status_history(transaction_id, status, occurred_at)
SELECT transaction_id, 'PENDING', now() - interval '1 day' FROM seed_transfers;
INSERT INTO ledger_entries(transaction_id, wallet_id, amount_paise, created_at)
SELECT transaction_id, wallet_id, -100, now() - interval '1 day' FROM seed_transfers;
INSERT INTO ledger_entries(transaction_id, wallet_id, amount_paise, created_at)
SELECT transaction_id, recipient_id, 100, now() - interval '1 day' FROM seed_transfers;
UPDATE transactions SET transaction_status = 'POSTED', posted_at = now() - interval '1 day'
WHERE transaction_id IN (SELECT transaction_id FROM seed_transfers);
INSERT INTO transaction_status_history(transaction_id, status, occurred_at)
SELECT transaction_id, 'POSTED', now() - interval '1 day' FROM seed_transfers;
UPDATE wallets w SET balance = posted.balance FROM (
    SELECT wallet_id, SUM(amount_paise)::bigint AS balance FROM ledger_entries
    WHERE wallet_id IS NOT NULL GROUP BY wallet_id
) posted WHERE posted.wallet_id = w.wallet_id;
INSERT INTO seed_batches VALUES ('1000-2000-100000-v1', clock_timestamp());
\else
\echo 'Seed already complete; no rows added.'
\endif
DO $$ BEGIN
    IF (SELECT count(*) FROM users) <> 1000 OR (SELECT count(*) FROM wallets) <> 2000
       OR (SELECT count(*) FROM transactions WHERE transaction_type = 'TRANSFER') <> 100000 THEN
        RAISE EXCEPTION 'Unexpected seed cardinality';
    END IF;
    IF EXISTS (SELECT transaction_id FROM ledger_entries GROUP BY transaction_id HAVING SUM(amount_paise) <> 0)
       OR EXISTS (SELECT 1 FROM wallets w LEFT JOIN (
           SELECT wallet_id, SUM(amount_paise) AS balance FROM ledger_entries GROUP BY wallet_id
       ) e USING (wallet_id) WHERE w.balance <> COALESCE(e.balance, 0)) THEN
        RAISE EXCEPTION 'Seed ledger is inconsistent';
    END IF;
END $$;
COMMIT;
SELECT (SELECT count(*) FROM users) AS users, (SELECT count(*) FROM wallets) AS wallets,
       (SELECT count(*) FROM transactions WHERE transaction_type = 'TRANSFER') AS transfers,
       (SELECT count(*) FROM transactions WHERE transaction_type = 'TOPUP') AS opening_topups;
