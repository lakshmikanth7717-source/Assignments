package com.airtribe.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

public final class StatementQuestionModels {
    private StatementQuestionModels() { }
    public record Question(@NotBlank @Size(max = 2000) String question) { }
    public record Plan(int type, String counterparty, LocalDate from, LocalDate to) { }
    public record QueryResult(BigInteger value, List<Long> transferIds, long counterpartyMatches) {
        public QueryResult { transferIds = List.copyOf(transferIds); }
    }
    public record Answer(int type, String counterparty, LocalDate from, LocalDate to,
                         String timeZone, BigInteger value, String unit, List<Long> transferIds) {
        public Answer { transferIds = List.copyOf(transferIds); }
    }
}
