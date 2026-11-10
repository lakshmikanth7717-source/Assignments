package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.repository.WalletRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcWalletRepository implements WalletRepository {
    private static final RowMapper<Wallet> WALLET = (row, index) -> new Wallet(
            row.getLong("wallet_id"), row.getLong("user_id"),
            row.getString("wallet_name"), row.getLong("balance"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWalletRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Wallet create(long userId, String walletName) {
        return jdbc.queryForObject("""
                INSERT INTO wallets (user_id, wallet_name) VALUES (:userId, :name)
                RETURNING wallet_id, user_id, wallet_name, balance
                """, Map.of("userId", userId, "name", walletName), WALLET);
    }

    @Override
    public Optional<Wallet> findById(long walletId) {
        return jdbc.query("""
                SELECT wallet_id, user_id, wallet_name, balance
                FROM wallets WHERE wallet_id = :id
                """, Map.of("id", walletId), WALLET).stream().findFirst();
    }

    @Override
    public boolean ownsAny(long userId, List<Long> walletIds) {
        if (walletIds.isEmpty()) { return false; }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM wallets
                    WHERE user_id = :userId AND wallet_id IN (:walletIds)
                )
                """, Map.of("userId", userId, "walletIds", walletIds), Boolean.class));
    }

    @Override
    public List<Wallet> findByIdsForUpdate(List<Long> sortedWalletIds) {
        if (sortedWalletIds.isEmpty()) { return List.of(); }
        return jdbc.query("""
                SELECT wallet_id, user_id, wallet_name, balance
                FROM wallets WHERE wallet_id IN (:walletIds)
                ORDER BY wallet_id
                FOR UPDATE
                """, Map.of("walletIds", sortedWalletIds), WALLET);
    }

    @Override
    public void updateBalance(long walletId, long newBalancePaise) {
        int updated = jdbc.update("""
                UPDATE wallets SET balance = :balance WHERE wallet_id = :id
                """, Map.of("balance", newBalancePaise, "id", walletId));
        if (updated != 1) { throw new IllegalStateException("Expected to update exactly one wallet"); }
    }
}
