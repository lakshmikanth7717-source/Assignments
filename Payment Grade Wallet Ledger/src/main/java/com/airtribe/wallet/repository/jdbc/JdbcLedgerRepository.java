package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.dto.WalletModels.StatementEntry;
import com.airtribe.wallet.repository.LedgerRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcLedgerRepository implements LedgerRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLedgerRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public OptionalLong findSystemAccountId(String accountType) {
        List<Long> ids = jdbc.query("""
                SELECT system_account_id FROM system_accounts WHERE account_type = :accountType
                """, Map.of("accountType", accountType), (row, index) -> row.getLong("system_account_id"));
        return ids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ids.get(0));
    }

    @Override
    public void insertEntries(long transactionId, List<Entry> entries) {
        if (entries.isEmpty()) { return; }
        MapSqlParameterSource[] batch = entries.stream().map(entry -> new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("walletId", entry.walletId(), Types.BIGINT)
                .addValue("systemAccountId", entry.systemAccountId(), Types.BIGINT)
                .addValue("amount", entry.amountPaise())).toArray(MapSqlParameterSource[]::new);
        int[] inserted = jdbc.batchUpdate("""
                INSERT INTO ledger_entries (transaction_id, wallet_id, system_account_id, amount_paise)
                VALUES (:transactionId, :walletId, :systemAccountId, :amount)
                """, batch);
        for (int count : inserted) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException("A ledger entry was not inserted");
            }
        }
    }

    @Override
    public List<Entry> findByTransactionId(long transactionId) {
        return jdbc.query("""
                SELECT wallet_id, system_account_id, amount_paise
                FROM ledger_entries WHERE transaction_id = :id ORDER BY ledger_entry_id
                """, Map.of("id", transactionId), (row, index) -> new Entry(
                row.getObject("wallet_id", Long.class), row.getObject("system_account_id", Long.class),
                row.getLong("amount_paise")));
    }

    @Override
    public List<StatementEntry> findStatement(long walletId, long offset, int limit) {
        return jdbc.query("""
                SELECT e.ledger_entry_id, e.transaction_id, e.amount_paise, e.created_at
                FROM ledger_entries e
                JOIN transactions t ON t.transaction_id = e.transaction_id
                WHERE e.wallet_id = :walletId AND t.transaction_status = 'POSTED'
                ORDER BY e.created_at DESC, e.ledger_entry_id DESC
                LIMIT :limit OFFSET :offset
                """, Map.of("walletId", walletId, "offset", offset, "limit", limit),
                (row, index) -> new StatementEntry(row.getLong("ledger_entry_id"),
                        row.getLong("transaction_id"), row.getLong("amount_paise"),
                        row.getTimestamp("created_at").toInstant()));
    }

    @Override
    public List<StatementEntry> findStatementAfter(long walletId, Instant createdAt, Long entryId, int limit) {
        if (createdAt == null) { return findStatement(walletId, 0, limit); }
        return jdbc.query("""
                SELECT e.ledger_entry_id, e.transaction_id, e.amount_paise, e.created_at
                FROM ledger_entries e JOIN transactions t ON t.transaction_id = e.transaction_id
                WHERE e.wallet_id = :walletId AND t.transaction_status = 'POSTED'
                  AND (e.created_at, e.ledger_entry_id) < (:createdAt, :entryId)
                ORDER BY e.created_at DESC, e.ledger_entry_id DESC LIMIT :limit
                """, Map.of("walletId", walletId, "createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                "entryId", entryId, "limit", limit), (row, index) -> new StatementEntry(
                row.getLong("ledger_entry_id"), row.getLong("transaction_id"), row.getLong("amount_paise"),
                row.getTimestamp("created_at").toInstant()));
    }

    @Override
    public BigInteger totalLedgerBalance()
    {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount_paise), 0)
            FROM ledger_entries
            """, Map.of(), BigInteger.class);
    }

    @Override
    public List<TransferBalance> totalTransferBalance() {
        return jdbc.query("""
                SELECT transaction_id,
                       SUM(amount_paise) AS imbalance_paise
                FROM ledger_entries
                GROUP BY transaction_id
                HAVING SUM(amount_paise) <> 0
                ORDER BY transaction_id
                """, Map.of(), (row, index) -> new TransferBalance(
                row.getLong("transaction_id"),
                row.getBigDecimal("imbalance_paise").toBigIntegerExact()));
    }

    @Override
    public List<WalletBalance> findWalletBalanceMismatches() {
        return jdbc.query("""
                SELECT w.wallet_id, w.balance AS stored_balance_paise,
                       COALESCE(l.computed_balance_paise, 0) AS computed_balance_paise
                FROM wallets w
                LEFT JOIN (
                    SELECT e.wallet_id, SUM(e.amount_paise) AS computed_balance_paise
                    FROM ledger_entries e
                    JOIN transactions t ON t.transaction_id = e.transaction_id
                    WHERE t.transaction_status = 'POSTED' AND e.wallet_id IS NOT NULL
                    GROUP BY e.wallet_id
                ) l ON l.wallet_id = w.wallet_id
                WHERE w.balance <> COALESCE(l.computed_balance_paise, 0)
                ORDER BY w.wallet_id
                """, Map.of(), (row, index) -> new WalletBalance(
                row.getLong("wallet_id"), row.getLong("stored_balance_paise"),
                row.getBigDecimal("computed_balance_paise").toBigIntegerExact()));
    }

    @Override
    public List<WalletBalance> findOverdrawnWallets() {
        return jdbc.query("""
                SELECT w.wallet_id, w.balance AS stored_balance_paise,
                       SUM(e.amount_paise) AS computed_balance_paise
                FROM wallets w
                JOIN ledger_entries e ON e.wallet_id = w.wallet_id
                JOIN transactions t ON t.transaction_id = e.transaction_id
                WHERE t.transaction_status = 'POSTED'
                GROUP BY w.wallet_id, w.balance
                HAVING SUM(e.amount_paise) < 0
                ORDER BY w.wallet_id
                """, Map.of(), (row, index) -> new WalletBalance(
                row.getLong("wallet_id"), row.getLong("stored_balance_paise"),
                row.getBigDecimal("computed_balance_paise").toBigIntegerExact()));
    }

    @Override
    public List<Long> findTransfersWithoutEntries() {
        return jdbc.query("""
                SELECT t.transaction_id
                FROM transactions t
                WHERE t.transaction_status = 'POSTED' AND NOT EXISTS (
                    SELECT 1 FROM ledger_entries e WHERE e.transaction_id = t.transaction_id
                )
                ORDER BY t.transaction_id
                """, Map.of(), (row, index) -> row.getLong("transaction_id"));
    }

    @Override
    public List<OrphanedEntry> findEntriesWithoutTransfers() {
        return jdbc.query("""
                SELECT e.ledger_entry_id, e.transaction_id
                FROM ledger_entries e
                WHERE NOT EXISTS (
                    SELECT 1 FROM transactions t WHERE t.transaction_id = e.transaction_id
                )
                ORDER BY e.ledger_entry_id
                """, Map.of(), (row, index) -> new OrphanedEntry(
                row.getLong("ledger_entry_id"), row.getLong("transaction_id")));
    }
}
