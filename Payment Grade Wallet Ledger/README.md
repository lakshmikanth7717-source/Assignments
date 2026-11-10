# Payment Grade Wallet Ledger

A Java 17 / Spring Boot API with a PostgreSQL 17 double-entry ledger, ordered wallet
locks, durable idempotency, risk holds, statement Q&A, and a separate reconciliation
worker. All amounts are whole INR paise. This is a reproducible local assignment;
the demo Basic Auth account is `admin` / `user123`, not a production identity system.

## Run every test with one command

After cloning branch `lakshmikanth/wallet-locking`, enter this directory:

```sh
cd "Payment Grade Wallet Ledger"
docker compose --profile test run --build --rm tests
```

Only Docker Engine/Desktop and Compose v2.24+ are required. No host Java, Maven,
PostgreSQL, `.env`, or OpenAI key is needed. The first run downloads dependencies.
A nonzero exit means failure. The suite uses a separate temporary PostgreSQL
service, applies Flyway from empty databases/schemas, and never silently skips
integration tests. `mvn verify` also works with Java 17+, Maven, and Docker
(Testcontainers), or with TEST_DATABASE_URL/USER/PASSWORD for a disposable database.

## Start API, Postgres, and worker

```sh
docker compose up --build -d
```

`docker compose up` also builds and starts all three. API: http://localhost:8081.
The authenticated `GET /health` checks the database. Only the API publishes a host
port, on loopback; use WALLET_API_PORT to change 8081. API scheduling is disabled;
the worker starts after API health and polls durable reconciliation requests every
second. Both startup paths validate the single Flyway migration. Flyway coordinates
concurrent startup. To stop services while retaining data, run `docker compose down`.

## Seed and demonstrations

On a fresh application database, seed before running the demo:

```sh
docker compose --profile tools run --rm seed
```

This creates exactly **1,000 users (including the bootstrap admin), 2,000 wallets,
and 100,000 posted transfers**, plus 2,000 balanced opening top-ups. Each wallet
starts with INR 10,000 and participates in 50 circular transfers of 100 paise,
leaving every cache backed by its ledger. The seed uses one transaction, an advisory
lock and a completion marker; repeating it adds nothing. It rejects a nonempty
financial database. It retains every financial trigger, validates counts and
balances before commit, and is a bulk fixture, not a throughput benchmark of the API.

```sh
docker compose --profile tools run --rm demo
docker compose --profile tools run --rm isolation
```

The HTTP demo asserts one posting end to end (including ledger details), an exact
retry, changed-body rejection, a velocity hold without movement, a reconciliation
run performed by the worker, a caller-scoped Q&A answer, and injection rejection.
It creates its own wallets and works after seeding or on an unseeded database.
Evidence is in [docs/evidence](docs/evidence). The five-minute recorded walkthrough
and its reproducible renderer are described in [docs/demo.md](docs/demo.md).
The isolation lab has commands and real two-session output in
[docs/isolation.md](docs/isolation.md). Nouns, cardinalities, and the ER diagram are
in [docs/schema.md](docs/schema.md).

## Design questions and decisions

**What is the source of truth?** Immutable, signed ledger entries in POSTED
transactions. `wallets.balance` is an indexed read cache. Top-ups debit the CASH_IN
system account and credit a wallet; withdrawals do the inverse into CASH_OUT.
A transfer debits one sender and credits 1-100 distinct recipients. Entries sum to
zero, positive total equals the header amount, and each participant occurs once.
`accounts` is a category catalog; `system_accounts` contains actual ledger parties.
No floating-point arithmetic or cross-currency conversion is used.

**Why READ COMMITTED and ordered locks?** Each money-moving path locks all known
wallets by ascending immutable ID, then reads/checks funds and updates ledger,
cache, status history and response in one transaction. A waiting spender sees the
committed balance. The balance CHECK provides a second guard. Consistent ordering
prevents wallet-to-wallet cycles; this is not a claim that all possible database
locks can never deadlock. SERIALIZABLE would add retries for a rule whose complete
row set is already known. Cross-wallet aggregate limits need further coordination.

**What does a retry mean?** UNIQUE(user_id, UUID key) serializes concurrent claims.
SHA-256 covers the operation, relevant path identity, and canonical validated DTO.
A successful request saves the exact status/body with its financial changes.
Duplicates roll back the failed INSERT transaction, then compare and replay in a
fresh transaction. A changed body, operation, or reversal target returns 422.
Different users can reuse a key. Failed requests roll back the claim and can be
retried after circumstances change. Completed responses never change, including
an original HELD response after later admin release. Keys have no expiry yet.

**Can history be edited?** PostgreSQL triggers reject changes/deletes to ledger
entries, posted transaction terms, status events, hold evidence, and risk decisions.
Reversal creates opposite entries under locks, checks funds, and permits at most
one posted reversal per original. The application posting protocol validates
cross-row sums; a privileged raw SQL writer can bypass that protocol. Reconciliation
detects such inconsistencies; database privileges should be tightened before deployment.

**What does HELD mean?** An immutable intent plus recipients and reason, with no
ledger entries, cache change, or reserved funds. An admin release rechecks funds
and atomically posts; insufficient funds leave it held. Velocity holds when more
than five prior outgoing transfers posted in ten minutes. The inherited historical
rule intentionally tests 30-day historical average > 5 x current amount (the
counterintuitive direction is preserved and documented). A first-time recipient
above INR 10,000 is advisory FLAG, not HOLD. Risk thresholds live in `risk_rules`.

**How is reconciliation consistent?** A worker locks one durable job row with
NOWAIT and reads one REPEATABLE READ snapshot. Five checks report global imbalance,
transaction imbalance, wallet cache drift, overdrafts, and orphaned records.
Reports persist six problem categories, paginated with expected/actual evidence.
Queue acceptance returns 202; status becomes PASSED, ISSUES_FOUND, or FAILED.
Audit transactions preserve partial failures separately. Multiple workers cannot
run the same job together. No automatic financial repair is attempted.

**Can prompt injection move money or reveal another user's history?** The model
only proposes an allowlisted JSON query plan. Strict field, duplicate-key, date,
and type validation precede parameterized SQL whose user scope comes exclusively
from the authenticated actor. No generated SQL, account IDs, tools, or stored
notes enter the query. Risk labels are advisory and cannot release a hold.
Tests inject hostile questions and model output, and assert unchanged money and
caller scope. These prove application boundaries; they do not prove a model never
misclassifies ordinary language.

**How can Q&A run without a key?** Default offline mode is explicitly a deterministic
parser accepting only `How many transfers from YYYY-MM-DD to YYYY-MM-DD?`. It rejects
all extra instructions. The answer still comes from real, caller-scoped PostgreSQL
queries. Risk review supplies a fixed review label. For model-backed parsing of
four question types, use the `live` Spring profile and supply OPENAI_API_KEY through
a local environment override; no credentials belong in the repo. The live model
has exactly the same validation and SQL boundaries. The offline demo is not an
LLM quality evaluation.

## Validation and CI

Stampede tests coordinate 20 real concurrent database lock waits: exactly one
8,000-paise spend succeeds against 10,000 paise. Idempotency tests cover concurrent
same-key retries, per-user scope, changed requests, rollback/retry, and cross-endpoint
and reversal-target reuse. Reconciliation tests inject each failure type and check
queue contention, snapshots, partial audits and reports. Prompt-injection tests use
controlled hostile model output and real SQL. Authentication tests verify role and
principal behavior. No network model calls are made by the tests.

The repository-root workflow `.github/workflows/payment-wallet-ledger.yml` runs the
same Docker test command, then starts all services, loads and repeats the full seed,
runs the HTTP demo and isolation lab, and uploads test and runtime evidence. This is
the sole file outside this project, required for GitHub to discover the workflow.

## Trade-offs and next steps

The Basic Auth demo maps every accepted HTTP request to one database administrator;
service-level tests exercise separate users, but this HTTP identity setup is not
multi-user authentication. Replace it with OIDC/JWT and explicit admin separation.
Add bounded deadlock retries, request timeouts and rate limits, scoped database
roles, metrics/alerts, key retention, and a recovery policy for runs left RUNNING
by worker crashes. Move live AI calls out of locked financial transactions using
a durable outbox: inherited risk assessment currently calls AI while locks are
held; offline mode has no external wait. Add provider budgets and live evaluation.

The schema is a fresh-install V1, not an upgrade for a database with an older V1
checksum. Pin container digests/dependency patches for a release and add backup,
restore and migration rehearsals. Real payment rails require provider idempotency,
an outbox, delivery retries, settlement reconciliation, and operational controls;
this project's top-up is a local ledger operation, not a bank integration.
