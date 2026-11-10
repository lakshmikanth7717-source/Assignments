package com.airtribe.wallet.repository;

import java.util.List;
import java.util.Optional;
import com.airtribe.wallet.dto.ReconciliationModels.RunSummary;
import com.airtribe.wallet.dto.ReconciliationModels.ProblemReport;

public interface ReconciliationRepository {
    enum RunStatus { QUEUED, RUNNING, PASSED, ISSUES_FOUND, FAILED }

    enum CheckName {
        GLOBAL_LEDGER_BALANCE,
        TRANSFER_BALANCE,
        WALLET_BALANCE,
        OVERDRAFT,
        TRANSFER_WITHOUT_ENTRIES,
        ENTRY_WITHOUT_TRANSFER
    }

    record Problem(CheckName checkName, String entityId, String expected, String actual) { }

    // Hold the transaction open on success; roll it back immediately on false.
    boolean tryLockJob(String jobName);

    long startRun(String jobName);

    long enqueueRun(String jobName, long requestedByUserId);

    List<Long> findQueuedRuns(int limit);

    boolean startQueuedRun(long runId);

    Optional<RunSummary> findRun(long runId);

    List<ProblemReport> findProblems(long runId, long offset, int limit);

    void finishRun(long runId, RunStatus status, int checksCompleted, List<Problem> problems, String errorMessage);
}
