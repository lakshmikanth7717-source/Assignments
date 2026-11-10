package com.airtribe.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class WalletModels {
    private WalletModels() { }

    public record OpenWalletRequest(@NotBlank @Size(max = 100) String walletName) { }

    public record WalletResponse(long walletId, long userId, String walletName) { }

    public record BalanceResponse(long walletId, long balancePaise, String currency) { }

    public record StatementEntry(long ledgerEntryId, long transactionId, long amountPaise,
                                 Instant createdAt) { }

    public record StatementResponse(long walletId, String currency, List<StatementEntry> entries,
                                    int page, int size, boolean hasMore, long offset, String nextCursor) {
        public StatementResponse {
            entries = List.copyOf(entries);
        }
    }
}
