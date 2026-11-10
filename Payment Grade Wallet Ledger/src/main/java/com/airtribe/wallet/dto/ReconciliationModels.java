package com.airtribe.wallet.dto;

import java.time.Instant;
import java.util.List;

public final class ReconciliationModels {
    private ReconciliationModels() { }
    public record AcceptedRun(long runId, String status) { }
    public record RunSummary(long runId, String jobName, Instant requestedAt, Instant startedAt,
                             Instant finishedAt, String status, int checksCompleted, long problemCount,
                             String errorMessage) { }
    public record ProblemReport(long problemId, String check, String id, String expected, String actual) { }
    public record RunReport(RunSummary run, List<ProblemReport> problems, int page, int size, boolean hasMore) {
        public RunReport { problems = List.copyOf(problems); }
    }
}
