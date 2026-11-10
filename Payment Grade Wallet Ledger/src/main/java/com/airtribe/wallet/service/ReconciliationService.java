package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.ReconciliationModels.*;
import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.repository.ReconciliationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {
    private final ReconciliationRepository reports;
    private final IdempotencyService idempotency;
    public ReconciliationService(ReconciliationRepository reports, IdempotencyService idempotency) {
        this.reports = reports;
        this.idempotency = idempotency;
    }

    public StoredHttpResponse enqueue(long adminId, UUID key) {
        return idempotency.execute(adminId, key, "RECONCILIATION", null, () -> {
            long runId = reports.enqueueRun("ledger_reconciliation", adminId);
            return idempotency.accepted(new AcceptedRun(runId, "QUEUED"));
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RunReport getReport(long runId, int page, int size) {
        RunSummary run = reports.findRun(runId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "Reconciliation run not found."));
        List<ProblemReport> problems = reports.findProblems(runId, (long) page * size, size + 1);
        boolean more = problems.size() > size;
        return new RunReport(run, more ? problems.subList(0, size) : problems, page, size, more);
    }
}
