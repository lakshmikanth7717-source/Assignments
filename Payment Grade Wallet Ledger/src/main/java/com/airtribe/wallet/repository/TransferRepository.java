package com.airtribe.wallet.repository;

import com.airtribe.wallet.dto.RuleModels.RecentTransfer;
import com.airtribe.wallet.dto.TransferModels.TransferDetailsResponse;
import com.airtribe.wallet.dto.TransferModels.TransferStatus;
import com.airtribe.wallet.dto.TransferModels.Recipient;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

public interface TransferRepository {
    enum Type { TOPUP, TRANSFER, WITHDRAWAL, REVERSAL }

    record Transfer(long transactionId, long initiatingWalletId, long initiatedByUserId, Type type,
                    long amountPaise, TransferStatus status, Long reversalOfTransactionId) { }

    long createPending(long initiatedByUserId, long initiatingWalletId, Type type,
                       long amountPaise, Long reversalOfTransactionId);

    // Transition PENDING -> POSTED and append status history in the caller's transaction.
    void markPosted(long transactionId);

    // Persist the intent separately from ledger entries; transition PENDING -> HELD.
    void hold(long transactionId, String ruleCode, String reason, List<Recipient> recipients);

    List<Recipient> findHeldRecipients(long transactionId);

    // Caller holds the transaction and wallet locks and has revalidated balances.
    void releaseHeld(long transactionId, long adminUserId);

    void cancelHeld(long transactionId);

    Optional<Transfer> findById(long transactionId);

    // Lock the original transaction row before checking/creating a reversal.
    Optional<Transfer> findByIdForUpdate(long transactionId);

    boolean hasPostedReversal(long originalTransactionId);

    // Count headers once per outgoing transfer, using database time after wallet locks are acquired.
    long countPostedOutgoingTransfers(long senderWalletId, int windowMinutes);

    Optional<BigDecimal> averagePostedOutgoingTransfers(long senderWalletId, int windowMinutes);

    // Up to ten outgoing TRANSFER headers, all statuses, newest creation time/ID first.
    List<RecentTransfer> findLast10OutgoingTransfers(long senderWalletId);

    // Lifetime history across all sender wallets; one batched lookup for recipient candidates.
    Set<Long> findPreviouslyPaidRecipientWalletIds(long senderUserId, List<Long> recipientWalletIds);

    Optional<TransferDetailsResponse> findDetailsById(long transactionId);
}
