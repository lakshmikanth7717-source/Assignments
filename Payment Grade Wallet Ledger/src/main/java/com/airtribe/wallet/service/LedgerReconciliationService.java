package com.airtribe.wallet.service;

import com.airtribe.wallet.repository.LedgerRepository;
import com.airtribe.wallet.repository.LedgerRepository.OrphanedEntry;
import com.airtribe.wallet.repository.LedgerRepository.TransferBalance;
import com.airtribe.wallet.repository.LedgerRepository.WalletBalance;
import com.airtribe.wallet.repository.ReconciliationRepository;
import com.airtribe.wallet.repository.ReconciliationRepository.CheckName;
import com.airtribe.wallet.repository.ReconciliationRepository.Problem;
import com.airtribe.wallet.repository.ReconciliationRepository.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class LedgerReconciliationService {
    private static final Logger LOG = LoggerFactory.getLogger(LedgerReconciliationService.class);
    private static final String JOB_NAME = "ledger_reconciliation";
    private final LedgerRepository ledger;
    private final ReconciliationRepository reports;
    private final TransactionTemplate checkTransactions;
    private final TransactionTemplate auditTransactions;

    public LedgerReconciliationService(LedgerRepository ledger, ReconciliationRepository reports,
                                       PlatformTransactionManager transactionManager) {
        this.ledger = ledger;
        this.reports = reports;
        this.checkTransactions = new TransactionTemplate(transactionManager);
        this.checkTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.checkTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // FOR UPDATE is not allowed in a PostgreSQL read-only transaction.
        // This transaction only locks the job row and reads financial data.
        this.auditTransactions = new TransactionTemplate(transactionManager);
        this.auditTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.auditTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void runLedgerChecks() {
        executeRun(null);
    }

    public void runQueuedChecks() {
        List<Long> queued = auditTransactions.execute(transaction -> reports.findQueuedRuns(10));
        for (long runId : Objects.requireNonNull(queued)) { executeRun(runId); }
    }

    private void executeRun(Long queuedRunId) {
        checkTransactions.executeWithoutResult(transaction -> {
            // This must be the first database operation. Hold the lock until this callback finishes.
            if (!reports.tryLockJob(JOB_NAME)) {
                // NOWAIT failure aborts the PostgreSQL transaction; perform no further SQL in it.
                transaction.setRollbackOnly();
                LOG.info("Skipping ledger reconciliation: another run holds the job lock.");
                return;
            }
            runChecks(queuedRunId);
        });
    }

    private void runChecks(Long queuedRunId) {
        // Commit RUNNING separately so a check failure or process crash cannot erase the run record.
        Long runId = auditTransactions.execute(transaction -> {
            if (queuedRunId == null) { return reports.startRun(JOB_NAME); }
            return reports.startQueuedRun(queuedRunId) ? queuedRunId : null;
        });
        // Another worker may already have completed this queued request.
        if (runId == null) { return; }
        List<Problem> problems = new ArrayList<>();
        int checksCompleted = 0;
        try {
            checkGlobalLedgerBalance(problems);
            checksCompleted++;
            checkTransactionBalances(problems);
            checksCompleted++;
            checkWalletBalanceConsistency(problems);
            checksCompleted++;
            checkNoOverdrafts(problems);
            checksCompleted++;
            checkOrphanedRecords(problems);
            checksCompleted++;

            RunStatus status = problems.isEmpty() ? RunStatus.PASSED : RunStatus.ISSUES_FOUND;
            finishRun(runId, status, checksCompleted, problems, null);
            LOG.info("Ledger reconciliation run {} finished: status={}, checksCompleted={}, problems={}.",
                    runId, status, checksCompleted, problems.size());
        } catch (RuntimeException failure) {
            try {
                // The check transaction may be aborted. A fresh audit transaction saves the partial report.
                finishRun(runId, RunStatus.FAILED, checksCompleted, problems, failure.toString());
            } catch (RuntimeException auditFailure) {
                failure.addSuppressed(auditFailure);
            }
            LOG.error("Ledger reconciliation run {} failed after {} checks.", runId, checksCompleted, failure);
            throw failure;
        }
    }

    private void finishRun(long runId, RunStatus status, int checksCompleted,
                           List<Problem> problems, String errorMessage) {
        // Problem rows and final status/counts commit together while the outer transaction still holds the job lock.
        auditTransactions.executeWithoutResult(transaction ->
                reports.finishRun(runId, status, checksCompleted, problems, errorMessage));
    }

    private void checkGlobalLedgerBalance(List<Problem> problems) {
        BigInteger balance = ledger.totalLedgerBalance();
        if (!balance.equals(BigInteger.ZERO)) {
            problems.add(new Problem(CheckName.GLOBAL_LEDGER_BALANCE, "GLOBAL", "0", balance.toString()));
            LOG.error("Global ledger imbalance: signed total is {} paise; total credits do not equal total debits.", balance);
            return;
        }
        LOG.info("Global ledger balance check passed: total credits equal total debits.");
    }

    private void checkTransactionBalances(List<Problem> problems) {
        List<TransferBalance> imbalancedTransfers = ledger.totalTransferBalance();
        if (imbalancedTransfers.isEmpty()) {
            LOG.info("Transaction balance check passed: no unbalanced transfers found.");
            return;
        }
        for (TransferBalance transfer : imbalancedTransfers) {
            problems.add(new Problem(CheckName.TRANSFER_BALANCE, Long.toString(transfer.transactionId()),
                    "0", transfer.imbalancePaise().toString()));
            LOG.error("Transaction ledger imbalance: transactionId={}, signed total is {} paise.",
                    transfer.transactionId(), transfer.imbalancePaise());
        }
    }

    private void checkWalletBalanceConsistency(List<Problem> problems) {
        List<WalletBalance> mismatches = ledger.findWalletBalanceMismatches();
        if (mismatches.isEmpty()) {
            LOG.info("Wallet balance consistency check passed: stored balances match posted ledger entries.");
            return;
        }
        for (WalletBalance wallet : mismatches) {
            problems.add(new Problem(CheckName.WALLET_BALANCE, Long.toString(wallet.walletId()),
                    wallet.computedBalancePaise().toString(), Long.toString(wallet.storedBalancePaise())));
            LOG.error("Wallet balance mismatch: walletId={}, stored={} paise, computed={} paise.",
                    wallet.walletId(), wallet.storedBalancePaise(), wallet.computedBalancePaise());
        }
    }

    private void checkNoOverdrafts(List<Problem> problems) {
        List<WalletBalance> overdrawnWallets = ledger.findOverdrawnWallets();
        if (overdrawnWallets.isEmpty()) {
            LOG.info("Overdraft check passed: no user wallet has a negative computed balance.");
            return;
        }
        for (WalletBalance wallet : overdrawnWallets) {
            problems.add(new Problem(CheckName.OVERDRAFT, Long.toString(wallet.walletId()),
                    ">= 0", wallet.computedBalancePaise().toString()));
            LOG.error("Wallet overdraft: walletId={}, computed balance={} paise, stored balance={} paise.",
                    wallet.walletId(), wallet.computedBalancePaise(), wallet.storedBalancePaise());
        }
    }

    private void checkOrphanedRecords(List<Problem> problems) {
        List<Long> transfersWithoutEntries = ledger.findTransfersWithoutEntries();
        List<OrphanedEntry> entriesWithoutTransfers = ledger.findEntriesWithoutTransfers();
        if (transfersWithoutEntries.isEmpty() && entriesWithoutTransfers.isEmpty()) {
            LOG.info("Orphan check passed: every transfer has entries and every entry has a transfer.");
            return;
        }
        for (long transactionId : transfersWithoutEntries) {
            problems.add(new Problem(CheckName.TRANSFER_WITHOUT_ENTRIES, Long.toString(transactionId),
                    ">= 1 ledger entry", "0 ledger entries"));
            LOG.error("Transfer has no ledger entries: transactionId={}.", transactionId);
        }
        for (OrphanedEntry entry : entriesWithoutTransfers) {
            problems.add(new Problem(CheckName.ENTRY_WITHOUT_TRANSFER, Long.toString(entry.ledgerEntryId()),
                    "transfer " + entry.transactionId() + " exists", "missing"));
            LOG.error("Ledger entry has no transfer: ledgerEntryId={}, transactionId={}.",
                    entry.ledgerEntryId(), entry.transactionId());
        }
    }
}
