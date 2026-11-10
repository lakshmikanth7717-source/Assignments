# Two-session isolation lab

Run from this project directory with Docker Compose:

```sh
docker compose up -d --build
docker compose --profile tools run --rm isolation
```

The script opens two independent PostgreSQL connections and a third observer. It
uses only `isolation_lab.wallets`, checks results with assertions, then removes
that disposable table and schema. It never modifies the application's wallets.
The lab intentionally omits the nonnegative CHECK and ledger so that the unsafe
interleaving is observable. The real schema includes both.

First, both sessions begin at READ COMMITTED and read 10,000 paise without a lock.
Both approve an 8,000-paise spend. A debits and credits, commits, then B applies
its already-approved spend and commits. Result: -6,000 and 16,000. Conservation
alone does not prevent overspending.

For a manual rerun, open two terminals with
`docker compose exec postgres psql -U wallet -d wallet`. In either session,
prepare the disposable table before starting the two transactions:

```sql
CREATE SCHEMA IF NOT EXISTS isolation_lab;
CREATE TABLE IF NOT EXISTS isolation_lab.wallets (
    wallet_id BIGINT PRIMARY KEY, balance BIGINT NOT NULL
);
TRUNCATE isolation_lab.wallets;
INSERT INTO isolation_lab.wallets VALUES (1, 10000), (2, 0);
```

Session A:

```sql
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT wallet_id, balance FROM isolation_lab.wallets
ORDER BY wallet_id FOR NO KEY UPDATE;
-- 1 | 10000
-- 2 |     0
-- Start session B's locking SELECT now, before continuing.
UPDATE isolation_lab.wallets SET balance = balance - 8000 WHERE wallet_id = 1;
UPDATE isolation_lab.wallets SET balance = balance + 8000 WHERE wallet_id = 2;
COMMIT;
```

Session B:

```sql
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT wallet_id, balance FROM isolation_lab.wallets
ORDER BY wallet_id FOR NO KEY UPDATE;
-- Blocks until A commits; then returns 1 | 2000, 2 | 8000.
-- Reject the requested 8000 because the returned sender balance is 2000.
ROLLBACK;
```

An observer verifies B actually waits, rather than assuming concurrency from a sleep:

```sql
SELECT pid, wait_event_type, wait_event FROM pg_stat_activity
WHERE datname = current_database() AND wait_event_type = 'Lock';
```

The automatic script records the actual commands, observed wait, and final balances
in [evidence/isolation.txt](evidence/isolation.txt). It fails if B does not block or
returns stale balances. The checked-in transcript comes from the executed lab.

The service locks all participating application wallets in ascending ID order with
`FOR UPDATE` (a stronger lock than the lab's `FOR NO KEY UPDATE`). It validates the
returned balances and posts entries, caches, status history, and saved response in
the same READ COMMITTED transaction. The 20-client stampede tests use a compatible
NO KEY UPDATE blocker so foreign-key checks can finish before clients reach the
wallet lock. They observe all 20 PostgreSQL lock waits before releasing it.

At READ COMMITTED a locking query that waits for an updater returns the committed
updated row. Under REPEATABLE READ, the corresponding concurrent change can raise
`40001`; SERIALIZABLE also protects predicate decisions but requires whole-unit
retries. Ordered row locks cover this known set of wallet rows without requiring
those retries for ordinary conflicting spends. They do not serialize arbitrary
queries over unknown future rows. A cross-wallet user limit would require a user
lock or SERIALIZABLE with retry. The implementation does not automatically retry
deadlocks; clients can retry transient failures with the same idempotency key.
