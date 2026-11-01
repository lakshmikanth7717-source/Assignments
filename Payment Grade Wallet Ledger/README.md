# Why ordered wallet locks are sufficient for transfers

This project uses **Read Committed plus `SELECT ... FOR UPDATE` on every participating wallet**, held until the transfer commits or rolls back. This is sufficient to prevent concurrent transfers from overspending a wallet when every operation that spends funds follows the same protocol.

The reasoning starts with the rules a transfer must preserve. The examples use [the wallet schema](docs/schema.md): monetary values are whole paise in `BIGINT`; `wallets.balance` is a cache; and posted `ledger_entries` are the source of truth. The no-overdraft rule is the policy assumed for these examples.

This README describes the intended implementation. It does not claim that the transfer service, idempotency handling, or database enforcement described here has already been built.

## 1. What must remain true?

For a transfer from one wallet to one or more recipients:

| Rule | Meaning |
| --- | --- |
| The sender has sufficient funds | The sender's balance must stay at or above zero. |
| Money is conserved | The amount removed equals the total amount credited. |
| The ledger explains each movement | Every balance change has a matching signed ledger entry. |
| Posting is atomic | Balances, entries, and the `POSTED` status commit together. |
| One request has one effect | Retrying the same idempotent request must not transfer money again. |

Locks protect the balance decision from concurrent changes. Correct posting logic preserves the amounts and entries. A database transaction keeps the changes together. Idempotency protects against duplicate requests. Each mechanism has a separate job.

## 2. Why the original transfer broke

The unsafe implementation did this:

```text
Read balance -> Check sufficient funds -> Wait -> Debit -> Credit -> Commit
```

With a sender balance of 10000 paise (Rs 100), two requests can interleave:

| Step | Request A | Request B |
| --- | --- | --- |
| 1 | Reads 10000 | |
| 2 | | Reads 10000 |
| 3 | Approves an 8000-paise transfer | Approves an 8000-paise transfer |
| 4 | Debits the sender to 2000 and commits | |
| 5 | | Debits the sender to -6000 and commits |

The reads were valid when performed. The decision became stale before the later debit. An atomic debit and credit can still make an incorrect business decision.

The observed experiment used 20 clients, each attempting one 8000-paise transfer. Starting balances were 10000 and 0; final balances were:

```text
wallet_id | balance
----------+--------
1         | -150000
2         |  160000
```

All 20 transfers took effect. Their combined balance stayed 10000, but the sender was overdrawn. This is an overdraft; the arithmetic updates did not lose any debits.

## 3. Move the protection before the decision

The protected sequence is:

```text
Begin -> Lock wallets -> Check locked balances -> Post movements -> Commit
```

For wallets 1 and 2, the locking query is:

```sql
BEGIN ISOLATION LEVEL READ COMMITTED;

SELECT wallet_id, user_id, balance
FROM wallets
WHERE wallet_id IN (1, 2)
ORDER BY wallet_id
FOR UPDATE;

-- Application logic continues on this same connection and transaction.
-- Validate both rows, check the returned sender balance, and post the transfer.
-- COMMIT on success; ROLLBACK on rejection or failure.
```

This is the start of the protocol, not a complete transfer script. `FOR UPDATE` returns the balances while acquiring row locks. Use those returned balances for the check; there is no need for a second query just to reread them.

Competing updates and conflicting locking reads wait. Ordinary `SELECT` queries can still read committed versions. The locks remain until the transaction ends. [PostgreSQL row locks](https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-ROWS)

The explicit isolation clause documents the choice even if Read Committed is already the configured default. Read Committed on its own does not lock an ordinary balance read.

Do not run the locking query in autocommit and then start a separate transfer transaction: its locks would already have been released.

## 4. Why a second spender cannot use the same money

Repeat the Rs 100 / Rs 80 example with both requests using the protocol:

| Step | Request A | Request B |
| --- | --- | --- |
| 1 | Locks both wallets; sender balance is 10000 | |
| 2 | | Attempts the locking query and waits |
| 3 | Checks 10000 >= 8000; posts the transfer | Waiting |
| 4 | Commits; sender balance is now 2000 | Can acquire the locks |
| 5 | | Reads 2000; rejects because 2000 < 8000 |

At Read Committed, after waiting for an updater to commit, a locking read can return the updated row. [PostgreSQL Read Committed behavior](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-READ-COMMITTED)

The balance argument is simple. Let `b` be the balance returned under the lock, and `a` be the requested positive debit:

```text
Accept only if b >= a.
Apply new_balance = b - a.
Therefore new_balance >= 0.
```

The lock keeps another writer from changing `b` between the check and the update. Starting from valid balances, each compliant transfer preserves the nonnegative-balance rule. Requests sharing a wallet take turns through this protected part of the operation. Transfers involving entirely separate wallets can proceed concurrently.

For the original 20-client workload, with no other deposits or withdrawals, the expected result becomes one successful transfer and 19 insufficient-funds rejections:

```text
Sender:   2000 paise (Rs 20)
Receiver: 8000 paise (Rs 80)
```

This is an expected result for a future protected implementation, not a reported test result.

## 5. Why the wallet IDs must be locked in a fixed order

Opposite-direction transfers touch the same rows:

```text
Transfer A: wallet 1 -> wallet 2
Transfer B: wallet 2 -> wallet 1
```

If each locks its sender first, A can hold wallet 1 while B holds wallet 2. Each then waits for the row held by the other: a deadlock.

Both must instead acquire wallet locks in ascending `wallet_id` order, regardless of transfer direction. One obtains wallet 1; the other waits without holding wallet 2. The first can finish.

For multiple recipients, sort all distinct participating wallet IDs and lock them in that order. The ordering column is the stable primary key. `WHERE wallet_id IN (1, 2)` alone specifies no lock order; the query above includes `ORDER BY wallet_id`.

A consistent order prevents this wallet-row cycle. Other locks can still create deadlocks; PostgreSQL detects a deadlock and aborts one transaction. [PostgreSQL deadlocks](https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-DEADLOCKS)

## 6. Why the ledger and cached balances stay aligned

Suppose wallet 1 sends Rs 100: Rs 60 to wallet 2 and Rs 40 to wallet 3.

| Wallet | Ledger entry in paise | Cached balance change |
| --- | ---: | ---: |
| 1 | -10000 | -10000 |
| 2 | +6000 | +6000 |
| 3 | +4000 | +4000 |
| Total | 0 | 0 |

The transaction header stores `transaction_amount_paise = 10000` and the initiating `wallet_id = 1`. All three entries reference its `transaction_id`.

For each wallet, the cached balance and its posted ledger sum change by exactly the same amount. If they agreed before the transfer, they agree afterward. The zero sum preserves the total money across the participating wallets.

This assumes the starting cache already matches posted history, including opening-balance entries. Locking does not repair an existing mismatch. The earlier balance-only isolation lab did not establish a complete opening ledger.

The posting implementation must validate entry signs, recipients, total amounts, and nonzero amounts. The schema's foreign keys and per-entry checks do not enforce the sum across entries.

Commit the header's `POSTED` status, all entries, and all cached balance changes together. If the receiver update fails, roll back the sender debit and entries too. Application exceptions must also trigger rollback through the transaction manager or explicit error handling.

If a `PENDING` transaction was committed separately before posting, a failed posting leaves that earlier record intact. After rolling back the movements, record `FAILED` separately as the schema describes. `PENDING` and `FAILED` transactions must have no committed ledger movements.

## 7. The complete application flow

The following is application pseudocode, not SQL to paste into psql:

```text
validate positive whole-paise amounts and recipient totals
reject sender-as-recipient for this transfer API

begin database transaction at READ COMMITTED
try:
    atomically claim or resolve the authenticated user's idempotency key
    if this is a completed matching request:
        finish this database transaction without new money movements
        return its saved response without posting another transfer

    lock every distinct participating wallet in ascending wallet_id order
    require every requested wallet to exist
    verify the authenticated user owns the sender wallet
    check the balance returned for the locked sender
    if insufficient:
        reject without committing any money movement

    create the transaction header as PENDING
    insert the negative sender entry and positive recipient entries
    require the entries to match the requested allocation and sum to zero
    update each cached balance by its corresponding ledger movement
    mark the transaction POSTED
    save the completed idempotency response
    commit
except:
    roll back if the database transaction is still active
    handle or propagate the failure
```

Use the schema's approved `account_id` classification when creating the header. Internal-transfer category semantics are still an open design decision; this README does not invent a new category. Wallet locks target `wallets`, since `accounts` currently stores categories rather than money balances.

The idempotency branch needs its own transaction completion and recovery handling. For rejected requests, any cached error response must follow the schema's response policy while committing no money movement.

## 8. What makes this conclusion conditional?

The approach is sufficient for the stated transfer invariants under these conditions:

- Every spend path checks funds while holding the relevant wallet locks. A withdrawal job that validates an earlier unlocked balance breaks the argument.
- Locks, checks, ledger writes, status changes, and balances use the same database transaction and connection.
- All participating wallets are known and exist; missing rows cause rejection. Locking a missing wallet does not reserve it.
- Each transfer preserves the accounting rules when executed by itself, and the initial state is valid.
- Shared-wallet conflicts are the dependencies needed for the balance rule. Additional shared business rules get additional coordination.

For example, one user can own multiple wallets in this schema. A daily spending limit across all of that user's wallets cannot be protected merely by locking the sender of each transfer. Two transfers from different wallets could both see enough daily allowance. A shared limit/counter row locked by every applicable operation, or a correctly designed Serializable workflow, can protect that additional rule.

## 9. What Serializable would change

Serializable is a valid alternative when correctly implemented. It protects against broader read/write dependencies, including decisions based on changing query results. It requires retry handling; it does not supply the business validation or ledger posting logic.

In the same-wallet example, concurrent transactions may read 10000. One commits the 8000 debit; a conflicting transaction fails, retries from the beginning, sees 2000, and rejects. PostgreSQL also spends resources tracking dependencies. Its performance relative to explicit locks depends on the workload. [PostgreSQL Serializable isolation](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-SERIALIZABLE)

For the current transfer rule, the complete set of wallet rows is identifiable. Ordered locks therefore give a direct explanation of correctness and a practical implementation at Read Committed. This choice does not make all queries globally serializable.

## 10. Failures and operational limits

- Keep transactions short. Avoid network calls or user interaction while holding wallet locks. A busy wallet becomes a point of contention.
- Handle deadlock failures (`40P01`) with a bounded whole-transaction retry policy. If using Serializable, also handle `40001`. Rerun all reads and decisions. An insufficient-funds rejection is a business outcome rather than a transient database failure. [PostgreSQL retry handling](https://www.postgresql.org/docs/current/mvcc-serialization-failure-handling.html)
- A connection failure during `COMMIT` can leave the caller unsure whether the transfer committed. Reuse the same idempotency key to resolve the outcome; do not submit a fresh transfer key blindly.
- Database rollback covers transactional database changes. External payments or notifications need separate coordination. Notifications should follow a successful commit, or use an outbox written with the transfer for reliable delivery.
- If overdrafts are forbidden for every wallet, a `CHECK (balance >= 0)` constraint is useful defense in depth. The supplied schema does not currently have it. It complements the locked check and does not replace balanced posting or idempotency.
- Posted ledger entries remain immutable. Corrections use a new reversing transaction under the same posting protocol.

The reason this approach works is that the balance used to authorize a debit remains protected until the debit and its matching accounting records commit together.
