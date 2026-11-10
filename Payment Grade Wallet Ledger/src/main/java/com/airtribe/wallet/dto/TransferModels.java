package com.airtribe.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TransferModels {
    private TransferModels() { }

    public record MoneyMovementRequest(@NotNull @Positive Long walletId,
                                       @NotNull @Positive Long amountPaise) { }

    public record Recipient(@NotNull @Positive Long walletId,
                            @NotNull @Positive Long amountPaise) { }

    public record TransferRequest(@NotNull @Positive Long senderWalletId,
                                  @NotEmpty @Size(max = 100) List<@NotNull @Valid Recipient> recipients) { }

    public enum TransferStatus { PENDING, POSTED, FAILED, HELD }

    public record TransferResponse(long transactionId, TransferStatus status, long amountPaise,
                                   String currency, Long reversalOfTransactionId) {
        @JsonProperty(value = "message", access = JsonProperty.Access.READ_ONLY)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String message() {
            return status == TransferStatus.HELD
                    ? "Your transfer is on hold until an admin releases it. No money has been moved."
                    : null;
        }
    }

    public record LedgerEntryResponse(long ledgerEntryId, Long walletId, Long systemAccountId,
                                      long amountPaise) { }

    public record StatusChange(TransferStatus status, Instant occurredAt) { }

    public record TransferDetailsResponse(TransferResponse transfer, List<LedgerEntryResponse> entries,
                                          List<StatusChange> statusHistory) {
        public TransferDetailsResponse {
            entries = List.copyOf(entries);
            statusHistory = List.copyOf(statusHistory);
        }
    }
}
