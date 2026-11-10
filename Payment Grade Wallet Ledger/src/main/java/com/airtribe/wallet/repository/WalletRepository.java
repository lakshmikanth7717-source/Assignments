package com.airtribe.wallet.repository;

import java.util.List;
import java.util.Optional;

public interface WalletRepository {
    record Wallet(long walletId, long userId, String walletName, long balancePaise) { }

    // New wallets must start at zero; opening money requires a posted top-up.
    Wallet create(long userId, String walletName);

    Optional<Wallet> findById(long walletId);

    boolean ownsAny(long userId, List<Long> walletIds);

    // SELECT ... ORDER BY wallet_id FOR UPDATE on the same connection/transaction.
    // Return existing rows only. The service rejects missing participants.
    List<Wallet> findByIdsForUpdate(List<Long> sortedWalletIds);

    // Caller already holds this wallet's row lock. Require exactly one updated row.
    void updateBalance(long walletId, long newBalancePaise);
}
