package com.airtribe.wallet.scheduler;

import com.airtribe.wallet.service.LedgerReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "wallet.worker.enabled", havingValue = "true")
public class LedgerScheduler {
    private final LedgerReconciliationService reconciliation;

    public LedgerScheduler(LedgerReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void runLedgerChecks() {
        reconciliation.runLedgerChecks();
    }

    @Scheduled(fixedDelayString = "${wallet.reconciliation.queue-poll-ms:1000}")
    public void runQueuedChecks() {
        reconciliation.runQueuedChecks();
    }
}
