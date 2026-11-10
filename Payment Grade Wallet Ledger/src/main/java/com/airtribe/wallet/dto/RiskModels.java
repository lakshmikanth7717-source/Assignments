package com.airtribe.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class RiskModels {
    private RiskModels() { }
    public enum Decision { RELEASE, REVERSE }
    public record DecisionRequest(@NotNull Decision decision, @NotBlank @Size(max = 1000) String reason) { }
    public record Assessment(String label, String explanation, String source) { }
    public record QueueItem(long transactionId, long walletId, long amountPaise, String status,
                            String kind, String ruleCode, String reason, Instant queuedAt,
                            String ruleName, Assessment assessment, Instant assessedAt, LocalDate assessmentDate) { }
    public record QueueResponse(List<QueueItem> transfers, int page, int size, boolean hasMore) {
        public QueueResponse { transfers = List.copyOf(transfers); }
    }
}
