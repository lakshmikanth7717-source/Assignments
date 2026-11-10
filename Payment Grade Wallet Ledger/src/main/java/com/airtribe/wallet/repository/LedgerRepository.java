package com.airtribe.wallet.repository;

import com.airtribe.wallet.dto.WalletModels.StatementEntry;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

public interface LedgerRepository {
    record Entry(Long walletId, Long systemAccountId, long amountPaise) {
        public Entry {
            if ((walletId == null) == (systemAccountId == null) || amountPaise == 0) {
                throw new IllegalArgumentException("A nonzero ledger entry must reference one wallet or system account");
            }
        }

        public static Entry wallet(long walletId, long amountPaise) {
            return new Entry(walletId, null, amountPaise);
        }

        public static Entry system(long accountId, long amountPaise) {
            return new Entry(null, accountId, amountPaise);
        }
    }

    record TransferBalance(long transactionId, BigInteger imbalancePaise) { }

    record WalletBalance(long walletId, long storedBalancePaise, BigInteger computedBalancePaise) { }

    record OrphanedEntry(long ledgerEntryId, long transactionId) { }

    // Actual system ledger account IDs, not the original accounts category catalog.
    OptionalLong findSystemAccountId(String accountType);

    void insertEntries(long transactionId, List<Entry> entries);

    List<Entry> findByTransactionId(long transactionId);

    // POSTED entries only, created_at DESC with ledger_entry_id DESC as tie-breaker.
    List<StatementEntry> findStatement(long walletId, long offset, int limit);

    List<StatementEntry> findStatementAfter(long walletId, Instant createdAt, Long entryId, int limit);

    BigInteger totalLedgerBalance();

    List<TransferBalance> totalTransferBalance();

    // Wallet balances are computed from POSTED entries; no entries means zero.
    List<WalletBalance> findWalletBalanceMismatches();

    List<WalletBalance> findOverdrawnWallets();

    List<Long> findTransfersWithoutEntries();

    List<OrphanedEntry> findEntriesWithoutTransfers();
}
