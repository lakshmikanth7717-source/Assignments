package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.ReconciliationModels.AcceptedRun;
import com.airtribe.wallet.dto.ReconciliationModels.RunReport;
import com.airtribe.wallet.dto.TransferModels.MoneyMovementRequest;
import com.airtribe.wallet.dto.TransferModels.Recipient;
import com.airtribe.wallet.dto.TransferModels.TransferRequest;
import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.repository.ReconciliationRepository.CheckName;
import com.airtribe.wallet.repository.ReconciliationRepository.Problem;
import com.airtribe.wallet.repository.jdbc.JdbcReconciliationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Timeout(60)
@Import({ReconciliationService.class, LedgerReconciliationService.class, JdbcReconciliationRepository.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReconciliationServiceTests extends ServiceIntegrationTestSupport {
    @Autowired private ReconciliationService reconciliation;
    @Autowired private LedgerReconciliationService checks;

    @DynamicPropertySource
    static void isolatedSchema(DynamicPropertyRegistry properties) {
        // Global checks must not see another test's deliberately corrupt fixtures.
        // Rebuilding the context after each test gives Flyway a fresh schema, without truncating shared data.
        String schema = "reconciliation_" + UUID.randomUUID().toString().replace("-", "");
        properties.add("spring.flyway.default-schema", () -> schema);
        properties.add("spring.datasource.hikari.data-source-properties.currentSchema", () -> schema);
    }

    @Test
    void aCleanLedgerPassesAllChecksWithoutChangingFinancialRecords() {
        Wallet sender = wallet();
        Wallet recipient = wallet();
        fund(sender, 10_000L);
        assertThat(transfers.transfer(sender.userId(), UUID.randomUUID(),
                new TransferRequest(sender.walletId(), List.of(new Recipient(recipient.walletId(), 2_000L))))
                .statusCode()).isEqualTo(201);
        FinancialSnapshot before = financialSnapshot();

        RunReport report = runChecks();

        assertFinished(report, "PASSED", 0);
        assertThat(financialSnapshot()).isEqualTo(before);
    }

    @Test
    void aCachedBalanceMismatchIsReportedWithoutRepairingTheWallet() {
        Wallet wallet = wallet();
        fund(wallet, 1_000L);
        jdbc.update("UPDATE wallets SET balance = 1200 WHERE wallet_id = ?", wallet.walletId());

        RunReport report = runChecks();

        assertFinished(report, "ISSUES_FOUND", 1);
        assertProblems(report, problem(CheckName.WALLET_BALANCE, wallet.walletId(), "1000", "1200"));
        assertThat(balance(wallet.walletId())).isEqualTo(1_200L);
    }

    @Test
    void aGlobalLedgerImbalanceIsReportedAlongWithItsUnbalancedTransaction() {
        Wallet wallet = wallet();
        long transactionId = singleEntryPosting(wallet, 500L);
        FinancialSnapshot before = financialSnapshot();

        RunReport report = runChecks();

        assertFinished(report, "ISSUES_FOUND", 2);
        assertProblems(report,
                new Problem(CheckName.GLOBAL_LEDGER_BALANCE, "GLOBAL", "0", "500"),
                problem(CheckName.TRANSFER_BALANCE, transactionId, "0", "500"));
        assertThat(financialSnapshot()).isEqualTo(before);
    }

    @Test
    void offsettingTransactionImbalancesAreDetectedEvenWhenTheGlobalSumIsZero() {
        Wallet credited = wallet();
        Wallet debited = wallet();
        fund(debited, 1_000L);
        long creditId = singleEntryPosting(credited, 500L);
        long debitId = singleEntryPosting(debited, -500L);

        RunReport report = runChecks();

        assertFinished(report, "ISSUES_FOUND", 2);
        assertProblems(report,
                problem(CheckName.TRANSFER_BALANCE, creditId, "0", "500"),
                problem(CheckName.TRANSFER_BALANCE, debitId, "0", "-500"));
    }

    @Test
    void aComputedOverdraftIsDetectedEvenWhenTheCachedBalanceIsNonnegative() {
        Wallet wallet = wallet();
        fund(wallet, 1_000L);
        long transactionId = pendingTransaction(wallet, 1_500L);
        walletEntry(transactionId, wallet.walletId(), -1_500L);
        jdbc.update("""
                INSERT INTO ledger_entries(transaction_id, system_account_id, amount_paise)
                VALUES (?, (SELECT system_account_id FROM system_accounts WHERE account_type = 'CASH_OUT'), 1500)
                """, transactionId);
        postFixture(transactionId);
        jdbc.update("UPDATE wallets SET balance = 0 WHERE wallet_id = ?", wallet.walletId());

        RunReport report = runChecks();

        assertFinished(report, "ISSUES_FOUND", 2);
        assertProblems(report,
                problem(CheckName.WALLET_BALANCE, wallet.walletId(), "-500", "0"),
                problem(CheckName.OVERDRAFT, wallet.walletId(), ">= 0", "-500"));
    }

    @Test
    void aPostedTransactionWithoutEntriesIsReported() {
        long transactionId = pendingTransaction(wallet(), 100L);
        postFixture(transactionId);

        RunReport report = runChecks();

        assertFinished(report, "ISSUES_FOUND", 1);
        assertProblems(report, problem(CheckName.TRANSFER_WITHOUT_ENTRIES, transactionId,
                ">= 1 ledger entry", "0 ledger entries"));
    }

    @Test
    void anEntryWithoutAParentTransactionIsReported() {
        Wallet wallet = wallet();
        long missingTransactionId = 9_000_000L;
        // This corruption cannot be inserted normally. Relax the FK only in this disposable schema.
        jdbc.execute("ALTER TABLE ledger_entries DROP CONSTRAINT ledger_entries_transaction_id_fkey");
        long entryId = jdbc.queryForObject("""
                INSERT INTO ledger_entries(transaction_id, wallet_id, amount_paise)
                VALUES (?, ?, 500) RETURNING ledger_entry_id
                """, Long.class, missingTransactionId, wallet.walletId());

        RunReport report = runChecks();

        assertFinished(report, "ISSUES_FOUND", 3);
        assertProblems(report,
                new Problem(CheckName.GLOBAL_LEDGER_BALANCE, "GLOBAL", "0", "500"),
                problem(CheckName.TRANSFER_BALANCE, missingTransactionId, "0", "500"),
                problem(CheckName.ENTRY_WITHOUT_TRANSFER, entryId,
                        "transfer " + missingTransactionId + " exists", "missing"));
    }

    @Test
    void unpostedTransactionsWithoutEntriesAreNotReportedAsMissingPostings() {
        Wallet sender = wallet();
        Wallet recipient = wallet();
        pendingTransaction(sender, 100L);
        long failedId = pendingTransaction(sender, 100L);
        jdbc.update("UPDATE transactions SET transaction_status = 'FAILED' WHERE transaction_id = ?", failedId);
        long heldId = pendingTransaction(sender, 100L);
        jdbc.update("""
                INSERT INTO transfer_holds(transaction_id, rule_code, reason) VALUES (?, 'TEST_HOLD', 'Test held intent')
                """, heldId);
        jdbc.update("""
                INSERT INTO held_transfer_recipients(transaction_id, wallet_id, amount_paise) VALUES (?, ?, 100)
                """, heldId, recipient.walletId());
        jdbc.update("UPDATE transactions SET transaction_status = 'HELD' WHERE transaction_id = ?", heldId);

        RunReport report = runChecks();

        assertFinished(report, "PASSED", 0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long.class)).isZero();
    }

    @Test
    void aRepeatedEnqueueRequestReturnsTheOriginalQueuedRun() throws Exception {
        long adminId = createUser("admin");
        UUID key = UUID.randomUUID();

        StoredHttpResponse original = reconciliation.enqueue(adminId, key);
        StoredHttpResponse replay = reconciliation.enqueue(adminId, key);

        assertThat(original.statusCode()).isEqualTo(202);
        assertThat(replay).isEqualTo(original);
        AcceptedRun accepted = json.readValue(original.body(), AcceptedRun.class);
        assertThat(accepted.status()).isEqualTo("QUEUED");
        RunReport report = reconciliation.getReport(accepted.runId(), 0, 100);
        assertThat(report.run().status()).isEqualTo("QUEUED");
        assertThat(report.run().requestedAt()).isNotNull();
        assertThat(report.run().startedAt()).isNull();
        assertThat(report.run().finishedAt()).isNull();
        assertThat(report.run().checksCompleted()).isZero();
        assertThat(report.run().problemCount()).isZero();
        assertThat(report.run().errorMessage()).isNull();
        assertThat(report.problems()).isEmpty();
        assertThat(runCount()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT requested_by_user_id FROM reconciliation_runs WHERE run_id = ?
                """, Long.class, accepted.runId())).isEqualTo(adminId);
    }

    @Test
    void aQueuedRunIsProcessedOnceAndItsReportRemainsStable() throws Exception {
        Wallet wallet = wallet();
        fund(wallet, 1_000L);
        long runId = enqueueRun();
        FinancialSnapshot before = financialSnapshot();

        checks.runQueuedChecks();
        RunReport completed = reconciliation.getReport(runId, 0, 100);
        checks.runQueuedChecks();

        assertFinished(completed, "PASSED", 0);
        assertThat(reconciliation.getReport(runId, 0, 100)).isEqualTo(completed);
        assertThat(runCount()).isEqualTo(1L);
        assertThat(financialSnapshot()).isEqualTo(before);
    }

    @Test
    void aQueuedRunRemainsQueuedDuringJobLockContentionAndSucceedsOnRetry() throws Exception {
        long runId = enqueueRun();
        RunReport queued = reconciliation.getReport(runId, 0, 100);
        var worker = Executors.newSingleThreadExecutor();
        try {
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    try (var statement = blocker.createStatement()) {
                        statement.executeQuery("""
                                SELECT job_name FROM job_locks WHERE job_name = 'ledger_reconciliation' FOR UPDATE
                                """).close();
                    }
                    // NOWAIT must return promptly instead of waiting for the held lock.
                    worker.submit(checks::runQueuedChecks).get(5, TimeUnit.SECONDS);
                    assertThat(reconciliation.getReport(runId, 0, 100)).isEqualTo(queued);
                    assertThat(runCount()).isEqualTo(1L);
                } finally {
                    blocker.rollback();
                }
            }
            checks.runQueuedChecks();
            assertFinished(reconciliation.getReport(runId, 0, 100), "PASSED", 0);
            assertThat(runCount()).isEqualTo(1L);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void overlappingManualRunsCreateOnlyOneAuditRun() throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner;
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    // Pause the winner's first check after it acquires the job lock and commits RUNNING.
                    try (var statement = blocker.createStatement()) {
                        statement.execute("LOCK TABLE ledger_entries IN ACCESS EXCLUSIVE MODE");
                    }
                    winner = workers.submit(checks::runLedgerChecks);
                    await().alias("first reconciliation committed its RUNNING audit")
                            .atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
                            .untilAsserted(() -> assertThat(jdbc.queryForObject("""
                                    SELECT COUNT(*) FROM reconciliation_runs WHERE status = 'RUNNING'
                                    """, Long.class)).isEqualTo(1L));
                    assertThat(winner.isDone()).isFalse();

                    workers.submit(checks::runLedgerChecks).get(5, TimeUnit.SECONDS);
                    assertThat(runCount()).isEqualTo(1L);
                    assertThat(latestReport().run().status()).isEqualTo("RUNNING");
                } finally {
                    blocker.rollback();
                }
            }
            winner.get(10, TimeUnit.SECONDS);
            assertFinished(latestReport(), "PASSED", 0);
            assertThat(runCount()).isEqualTo(1L);
        } finally {
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void aFailedCheckPersistsItsPartialReportAndReleasesTheJobLock() {
        long transactionId = singleEntryPosting(wallet(), 500L);
        // Force a genuine SQL error in check three, after the first two have found problems.
        jdbc.execute("ALTER TABLE wallets RENAME COLUMN balance TO unavailable_balance");
        try {
            assertThatThrownBy(checks::runLedgerChecks).isInstanceOf(DataAccessException.class);
        } finally {
            jdbc.execute("ALTER TABLE wallets RENAME COLUMN unavailable_balance TO balance");
        }

        RunReport failed = latestReport();
        assertThat(failed.run().status()).isEqualTo("FAILED");
        assertThat(failed.run().checksCompleted()).isEqualTo(2);
        assertThat(failed.run().problemCount()).isEqualTo(2L);
        assertThat(failed.run().startedAt()).isNotNull();
        assertThat(failed.run().finishedAt()).isNotNull();
        assertThat(failed.run().errorMessage()).contains("balance");
        assertProblems(failed,
                new Problem(CheckName.GLOBAL_LEDGER_BALANCE, "GLOBAL", "0", "500"),
                problem(CheckName.TRANSFER_BALANCE, transactionId, "0", "500"));

        RunReport retried = runChecks();
        assertFinished(retried, "ISSUES_FOUND", 2);
        assertThat(retried.run().runId()).isNotEqualTo(failed.run().runId());
        assertThat(runCount()).isEqualTo(2L);
    }

    @Test
    void problemReportsArePaginatedWithoutChangingTheSummaryOrRepeatingProblems() {
        Wallet first = wallet();
        Wallet second = wallet();
        jdbc.update("UPDATE wallets SET balance = 10 WHERE wallet_id = ?", first.walletId());
        jdbc.update("UPDATE wallets SET balance = 20 WHERE wallet_id = ?", second.walletId());
        RunReport full = runChecks();

        RunReport pageZero = reconciliation.getReport(full.run().runId(), 0, 1);
        RunReport pageOne = reconciliation.getReport(full.run().runId(), 1, 1);

        assertFinished(full, "ISSUES_FOUND", 2);
        assertThat(pageZero.run()).isEqualTo(full.run());
        assertThat(pageOne.run()).isEqualTo(full.run());
        assertThat(pageZero.page()).isZero();
        assertThat(pageOne.page()).isEqualTo(1);
        assertThat(pageZero.size()).isEqualTo(1);
        assertThat(pageOne.size()).isEqualTo(1);
        assertThat(pageZero.hasMore()).isTrue();
        assertThat(pageOne.hasMore()).isFalse();
        assertProblems(pageZero, problem(CheckName.WALLET_BALANCE, first.walletId(), "0", "10"));
        assertProblems(pageOne, problem(CheckName.WALLET_BALANCE, second.walletId(), "0", "20"));
        assertThat(pageZero.problems().get(0).problemId()).isNotEqualTo(pageOne.problems().get(0).problemId());
    }

    @Test
    void anUnknownRunReturnsTheRunNotFoundError() {
        assertThatThrownBy(() -> reconciliation.getReport(9_000_000L, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(failure.code()).isEqualTo("RUN_NOT_FOUND");
                });
    }

    private RunReport runChecks() {
        checks.runLedgerChecks();
        return latestReport();
    }

    private RunReport latestReport() {
        long runId = jdbc.queryForObject("SELECT MAX(run_id) FROM reconciliation_runs", Long.class);
        return reconciliation.getReport(runId, 0, 100);
    }

    private long enqueueRun() throws Exception {
        StoredHttpResponse response = reconciliation.enqueue(createUser("admin"), UUID.randomUUID());
        assertThat(response.statusCode()).isEqualTo(202);
        return json.readValue(response.body(), AcceptedRun.class).runId();
    }

    private long runCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_runs", Long.class);
    }

    private void assertFinished(RunReport report, String status, int problemCount) {
        assertThat(report.run().jobName()).isEqualTo("ledger_reconciliation");
        assertThat(report.run().status()).isEqualTo(status);
        assertThat(report.run().checksCompleted()).isEqualTo(5);
        assertThat(report.run().problemCount()).isEqualTo((long) problemCount);
        assertThat(report.problems()).hasSize(problemCount);
        assertThat(report.run().requestedAt()).isNotNull();
        assertThat(report.run().startedAt()).isNotNull();
        assertThat(report.run().finishedAt()).isAfterOrEqualTo(report.run().startedAt());
        assertThat(report.run().errorMessage()).isNull();
        assertThat(report.hasMore()).isFalse();
    }

    private void assertProblems(RunReport report, Problem... expected) {
        assertThat(report.problems()).allSatisfy(problem -> assertThat(problem.problemId()).isPositive());
        assertThat(report.problems().stream().map(problem -> new Problem(CheckName.valueOf(problem.check()),
                problem.id(), problem.expected(), problem.actual())).toList()).containsExactlyInAnyOrder(expected);
    }

    private Problem problem(CheckName check, long entityId, String expected, String actual) {
        return new Problem(check, Long.toString(entityId), expected, actual);
    }

    private Wallet wallet() {
        long userId = createUser("reconciliation");
        return new Wallet(userId, createWallet(userId, "Reconciliation fixture"));
    }

    private void fund(Wallet wallet, long amount) {
        assertThat(transfers.topUp(wallet.userId(), UUID.randomUUID(),
                new MoneyMovementRequest(wallet.walletId(), amount)).statusCode()).isEqualTo(201);
    }

    private long pendingTransaction(Wallet wallet, long amount) {
        // Direct SQL intentionally permits ledger defects that the transfer service would reject.
        long id = jdbc.queryForObject("""
                INSERT INTO transactions(wallet_id, initiated_by_user_id, transaction_type,
                                         transaction_amount_paise, account_id)
                VALUES (?, ?, 'TRANSFER', ?,
                        (SELECT account_id FROM accounts WHERE account_type = 'INTERNAL_TRANSFER'))
                RETURNING transaction_id
                """, Long.class, wallet.walletId(), wallet.userId(), amount);
        jdbc.update("INSERT INTO transaction_status_history(transaction_id, status) VALUES (?, 'PENDING')", id);
        return id;
    }

    private void walletEntry(long transactionId, long walletId, long amount) {
        jdbc.update("INSERT INTO ledger_entries(transaction_id, wallet_id, amount_paise) VALUES (?, ?, ?)",
                transactionId, walletId, amount);
    }

    private void postFixture(long transactionId) {
        jdbc.update("""
                UPDATE transactions SET transaction_status = 'POSTED', posted_at = clock_timestamp()
                WHERE transaction_id = ?
                """, transactionId);
        jdbc.update("""
                INSERT INTO transaction_status_history(transaction_id, status, occurred_at)
                SELECT transaction_id, transaction_status, posted_at FROM transactions WHERE transaction_id = ?
                """, transactionId);
    }

    private long singleEntryPosting(Wallet wallet, long signedAmount) {
        long id = pendingTransaction(wallet, Math.abs(signedAmount));
        walletEntry(id, wallet.walletId(), signedAmount);
        jdbc.update("UPDATE wallets SET balance = balance + ? WHERE wallet_id = ?", signedAmount, wallet.walletId());
        postFixture(id);
        return id;
    }

    private FinancialSnapshot financialSnapshot() {
        return new FinancialSnapshot(
                jdbc.queryForList("SELECT * FROM wallets ORDER BY wallet_id"),
                jdbc.queryForList("SELECT * FROM transactions ORDER BY transaction_id"),
                jdbc.queryForList("SELECT * FROM ledger_entries ORDER BY ledger_entry_id"),
                jdbc.queryForList("SELECT * FROM transaction_status_history ORDER BY status_history_id"));
    }

    private record Wallet(long userId, long walletId) { }

    private record FinancialSnapshot(List<Map<String, Object>> wallets, List<Map<String, Object>> transactions,
                                     List<Map<String, Object>> entries, List<Map<String, Object>> statusHistory) { }
}
