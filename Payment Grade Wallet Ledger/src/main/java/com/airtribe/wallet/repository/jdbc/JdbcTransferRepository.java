package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.dto.RuleModels.RecentTransfer;
import com.airtribe.wallet.dto.TransferModels.LedgerEntryResponse;
import com.airtribe.wallet.dto.TransferModels.StatusChange;
import com.airtribe.wallet.dto.TransferModels.TransferDetailsResponse;
import com.airtribe.wallet.dto.TransferModels.TransferResponse;
import com.airtribe.wallet.dto.TransferModels.TransferStatus;
import com.airtribe.wallet.dto.TransferModels.Recipient;
import com.airtribe.wallet.repository.TransferRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcTransferRepository implements TransferRepository {
    private static final String TRANSFER_COLUMNS = """
            SELECT transaction_id, wallet_id, initiated_by_user_id, transaction_type, transaction_amount_paise,
                   transaction_status, reversal_of_transaction_id FROM transactions
            """;
    private static final RowMapper<Transfer> TRANSFER = (row, index) -> new Transfer(
            row.getLong("transaction_id"), row.getLong("wallet_id"),
            row.getLong("initiated_by_user_id"),
            Type.valueOf(row.getString("transaction_type")), row.getLong("transaction_amount_paise"),
            TransferStatus.valueOf(row.getString("transaction_status")),
            row.getObject("reversal_of_transaction_id", Long.class));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTransferRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public long createPending(long initiatedByUserId, long initiatingWalletId, Type type,
                              long amountPaise, Long reversalOfTransactionId) {
        String category = switch (type) {
            case TOPUP -> "CASH_IN";
            case TRANSFER -> "INTERNAL_TRANSFER";
            case WITHDRAWAL -> "CASH_OUT";
            case REVERSAL -> "REVERSAL";
        };
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("initiatedByUserId", initiatedByUserId)
                .addValue("walletId", initiatingWalletId).addValue("type", type.name())
                .addValue("amount", amountPaise).addValue("category", category)
                .addValue("reversalOf", reversalOfTransactionId, Types.BIGINT);
        long id = jdbc.queryForObject("""
                INSERT INTO transactions
                    (wallet_id, initiated_by_user_id, transaction_type, transaction_amount_paise, account_id,
                     reversal_of_transaction_id)
                VALUES (:walletId, :initiatedByUserId, CAST(:type AS transaction_type), :amount,
                    (SELECT account_id FROM accounts WHERE account_type = CAST(:category AS account_type)),
                    :reversalOf)
                RETURNING transaction_id
                """, params, Long.class);
        insertStatusHistory(id);
        return id;
    }

    @Override
    public void markPosted(long transactionId) {
        Transfer transfer = findByIdForUpdate(transactionId)
                .orElseThrow(() -> new IllegalStateException("Transfer to post was not found"));
        if (transfer.status() != TransferStatus.PENDING) {
            throw new IllegalStateException("Only a pending transfer can be posted");
        }
        // The header lock also prevents concurrent ledger inserts while the totals are checked.
        int updated = jdbc.update("""
                UPDATE transactions t
                SET transaction_status = 'POSTED', posted_at = clock_timestamp()
                WHERE t.transaction_id = :id AND t.transaction_status = 'PENDING'
                  AND (
                      SELECT COUNT(*) >= 2
                         AND SUM(e.amount_paise) = 0
                         AND SUM(e.amount_paise) FILTER (WHERE e.amount_paise > 0)
                             = t.transaction_amount_paise
                      FROM ledger_entries e WHERE e.transaction_id = t.transaction_id
                  )
                """, Map.of("id", transactionId));
        if (updated != 1) { throw new IllegalStateException("Transfer entries must balance and match its amount"); }
        insertStatusHistory(transactionId);
    }

    private void insertStatusHistory(long transactionId) {
        int inserted = jdbc.update("""
                INSERT INTO transaction_status_history (transaction_id, status, occurred_at)
                SELECT transaction_id, transaction_status, COALESCE(posted_at, clock_timestamp())
                FROM transactions WHERE transaction_id = :id
                """, Map.of("id", transactionId));
        if (inserted != 1) { throw new IllegalStateException("Expected to record one status change"); }
    }

    @Override
    public void hold(long transactionId, String ruleCode, String reason, List<Recipient> recipients) {
        int updated = jdbc.update("""
                UPDATE transactions SET transaction_status = 'HELD'
                WHERE transaction_id = :id AND transaction_type = 'TRANSFER' AND transaction_status = 'PENDING'
                """, Map.of("id", transactionId));
        if (updated != 1) { throw new IllegalStateException("Only a pending transfer can be held"); }
        jdbc.update("""
                INSERT INTO transfer_holds (transaction_id, rule_code, reason)
                VALUES (:id, :ruleCode, :reason)
                """, Map.of("id", transactionId, "ruleCode", ruleCode, "reason", reason));
        MapSqlParameterSource[] batch = recipients.stream().map(recipient -> new MapSqlParameterSource()
                .addValue("id", transactionId).addValue("walletId", recipient.walletId())
                .addValue("amount", recipient.amountPaise())).toArray(MapSqlParameterSource[]::new);
        for (int count : jdbc.batchUpdate("""
                INSERT INTO held_transfer_recipients (transaction_id, wallet_id, amount_paise)
                VALUES (:id, :walletId, :amount)
                """, batch)) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException("A held recipient was not inserted");
            }
        }
        insertStatusHistory(transactionId);
    }

    @Override
    public List<Recipient> findHeldRecipients(long transactionId) {
        return jdbc.query("""
                SELECT wallet_id, amount_paise FROM held_transfer_recipients
                WHERE transaction_id = :id ORDER BY wallet_id
                """, Map.of("id", transactionId), (row, index) ->
                new Recipient(row.getLong("wallet_id"), row.getLong("amount_paise")));
    }

    @Override
    public void releaseHeld(long transactionId, long adminUserId) {
        int updated = jdbc.update("""
                UPDATE transactions SET transaction_status = 'PENDING'
                WHERE transaction_id = :id AND transaction_status = 'HELD' AND transaction_type = 'TRANSFER'
                """, Map.of("id", transactionId));
        if (updated != 1) { throw new IllegalStateException("Only a held transfer can be released"); }
        int audited = jdbc.update("""
                UPDATE transfer_holds SET released_by = :adminId, released_at = clock_timestamp()
                WHERE transaction_id = :id AND released_at IS NULL
                """, Map.of("id", transactionId, "adminId", adminUserId));
        if (audited != 1) { throw new IllegalStateException("Expected one unreleased hold"); }
        insertStatusHistory(transactionId);
    }

    @Override
    public Optional<Transfer> findById(long transactionId) {
        return jdbc.query(TRANSFER_COLUMNS + " WHERE transaction_id = :id",
                Map.of("id", transactionId), TRANSFER).stream().findFirst();
    }

    @Override
    public void cancelHeld(long transactionId) {
        int updated = jdbc.update("""
                UPDATE transactions SET transaction_status = 'FAILED'
                WHERE transaction_id = :id AND transaction_status = 'HELD' AND transaction_type = 'TRANSFER'
                """, Map.of("id", transactionId));
        if (updated != 1) { throw new IllegalStateException("Only a held transfer can be cancelled"); }
        insertStatusHistory(transactionId);
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(long transactionId) {
        return jdbc.query(TRANSFER_COLUMNS + " WHERE transaction_id = :id FOR UPDATE",
                Map.of("id", transactionId), TRANSFER).stream().findFirst();
    }

    @Override
    public boolean hasPostedReversal(long originalTransactionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM transactions
                    WHERE reversal_of_transaction_id = :id AND transaction_status = 'POSTED'
                )
                """, Map.of("id", originalTransactionId), Boolean.class));
    }

    @Override
    public long countPostedOutgoingTransfers(long senderWalletId, int windowMinutes) {
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("The transfer count window must be positive");
        }
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions
                WHERE wallet_id = :walletId
                  AND transaction_type = 'TRANSFER' AND transaction_status = 'POSTED'
                  AND posted_at >= statement_timestamp() - (:windowMinutes * INTERVAL '1 minute')
                  AND posted_at <= statement_timestamp()
                """, Map.of("walletId", senderWalletId, "windowMinutes", windowMinutes), Long.class);
    }

    @Override
    public Optional<BigDecimal> averagePostedOutgoingTransfers(long senderWalletId, int windowMinutes) {
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("The transfer average window must be positive");
        }
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT AVG(transaction_amount_paise) FROM transactions
                WHERE wallet_id = :walletId
                  AND transaction_type = 'TRANSFER' AND transaction_status = 'POSTED'
                  AND posted_at >= statement_timestamp() - (:windowMinutes * INTERVAL '1 minute')
                  AND posted_at <= statement_timestamp()
                """, Map.of("walletId", senderWalletId, "windowMinutes", windowMinutes), BigDecimal.class));
    }

    @Override
    public List<RecentTransfer> findLast10OutgoingTransfers(long senderWalletId) {
        return jdbc.query("""
                SELECT transaction_id, wallet_id, initiated_by_user_id, transaction_amount_paise,
                       transaction_status, transaction_date
                FROM transactions
                WHERE wallet_id = :walletId AND transaction_type = 'TRANSFER'
                ORDER BY transaction_date DESC, transaction_id DESC
                LIMIT 10
                """, Map.of("walletId", senderWalletId), (row, index) -> new RecentTransfer(
                row.getLong("transaction_id"), row.getLong("wallet_id"), row.getLong("initiated_by_user_id"),
                row.getLong("transaction_amount_paise"), TransferStatus.valueOf(row.getString("transaction_status")),
                row.getTimestamp("transaction_date").toInstant()));
    }

    @Override
    public Set<Long> findPreviouslyPaidRecipientWalletIds(long senderUserId, List<Long> recipientWalletIds) {
        if (recipientWalletIds.isEmpty()) { return Set.of(); }
        return Set.copyOf(jdbc.query("""
                SELECT candidate.wallet_id FROM wallets candidate
                WHERE candidate.wallet_id IN (:recipientWalletIds)
                  AND EXISTS (
                      SELECT 1 FROM ledger_entries e
                      JOIN transactions t ON t.transaction_id = e.transaction_id
                      WHERE e.wallet_id = candidate.wallet_id AND e.amount_paise > 0
                        AND t.initiated_by_user_id = :senderUserId
                        AND t.transaction_type = 'TRANSFER' AND t.transaction_status = 'POSTED'
                        AND t.posted_at <= statement_timestamp()
                  )
                """, Map.of("senderUserId", senderUserId, "recipientWalletIds", recipientWalletIds),
                (row, index) -> row.getLong("wallet_id")));
    }

    @Override
    public Optional<TransferDetailsResponse> findDetailsById(long transactionId) {
        // One SELECT gives the header, entries and history a consistent committed snapshot.
        return jdbc.query("""
                SELECT t.transaction_id, t.transaction_amount_paise, t.transaction_status,
                       t.reversal_of_transaction_id,
                       e.ledger_entry_id, e.wallet_id, e.system_account_id, e.amount_paise,
                       h.status_history_id, h.status AS event_status, h.occurred_at
                FROM transactions t
                LEFT JOIN ledger_entries e ON e.transaction_id = t.transaction_id
                LEFT JOIN transaction_status_history h ON h.transaction_id = t.transaction_id
                WHERE t.transaction_id = :id
                ORDER BY e.ledger_entry_id, h.status_history_id
                """, Map.of("id", transactionId), rows -> {
            if (!rows.next()) { return Optional.empty(); }
            TransferResponse transfer = new TransferResponse(rows.getLong("transaction_id"),
                    TransferStatus.valueOf(rows.getString("transaction_status")),
                    rows.getLong("transaction_amount_paise"), "INR",
                    rows.getObject("reversal_of_transaction_id", Long.class));
            // Joining two child collections repeats rows; retain each entry/event once, in order.
            Map<Long, LedgerEntryResponse> entries = new LinkedHashMap<>();
            Map<Long, StatusChange> history = new LinkedHashMap<>();
            do {
                Long entryId = rows.getObject("ledger_entry_id", Long.class);
                if (entryId != null) {
                    entries.putIfAbsent(entryId, new LedgerEntryResponse(entryId,
                            rows.getObject("wallet_id", Long.class), rows.getObject("system_account_id", Long.class),
                            rows.getLong("amount_paise")));
                }
                Long historyId = rows.getObject("status_history_id", Long.class);
                if (historyId != null) {
                    history.putIfAbsent(historyId, new StatusChange(
                            TransferStatus.valueOf(rows.getString("event_status")),
                            rows.getTimestamp("occurred_at").toInstant()));
                }
            } while (rows.next());
            return Optional.of(new TransferDetailsResponse(transfer,
                    new ArrayList<>(entries.values()), new ArrayList<>(history.values())));
        });
    }
}
