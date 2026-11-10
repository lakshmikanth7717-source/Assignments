package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.dto.RiskModels.*;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import com.airtribe.wallet.repository.RiskRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcRiskRepository implements RiskRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcRiskRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void flag(long transactionId, RuleResult result) {
        jdbc.update("""
                INSERT INTO transfer_flags (transaction_id, rule_code, reason) VALUES (:id, :rule, :reason)
                """, Map.of("id", transactionId, "rule", result.ruleCode(), "reason", result.reason()));
        if (result.flaggedRecipients().isEmpty()) { return; }
        MapSqlParameterSource[] batch = result.flaggedRecipients().stream().map(recipient ->
                new MapSqlParameterSource().addValue("id", transactionId).addValue("walletId", recipient.walletId()))
                .toArray(MapSqlParameterSource[]::new);
        for (int count : jdbc.batchUpdate("""
                INSERT INTO transfer_flagged_recipients (transaction_id, wallet_id) VALUES (:id, :walletId)
                """, batch)) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException("Flagged recipient was not saved");
            }
        }
    }

    @Override
    public void saveAssessment(long transactionId, RuleResult result, Assessment assessment) {
        int inserted = jdbc.update("""
                INSERT INTO risk_flags
                    (transaction_id, rule_code, rule_name, rule_action, reason, label, explanation, assessment_source)
                VALUES (:id, :code, :name, :action, :reason, :label, :explanation, :source)
                """, new MapSqlParameterSource().addValue("id", transactionId)
                .addValue("code", result.ruleCode()).addValue("name", result.ruleName())
                .addValue("action", result.action().name()).addValue("reason", result.reason())
                .addValue("label", assessment.label()).addValue("explanation", assessment.explanation())
                .addValue("source", assessment.source()));
        if (inserted != 1) { throw new IllegalStateException("Expected to save one risk assessment"); }
    }

    @Override
    public boolean isFlagged(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM transfer_flags WHERE transaction_id = :id)
                """, Map.of("id", id), Boolean.class));
    }

    @Override
    public boolean hasDecision(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM transfer_risk_decisions WHERE transaction_id = :id)
                """, Map.of("id", id), Boolean.class));
    }

    @Override
    public void recordDecision(long id, long adminId, DecisionRequest request) {
        jdbc.update("""
                INSERT INTO transfer_risk_decisions (transaction_id, action, admin_user_id, reason)
                VALUES (:id, :action, :admin, :reason)
                """, Map.of("id", id, "action", request.decision().name(), "admin", adminId, "reason", request.reason().trim()));
    }

    @Override
    public List<QueueItem> findQueue(long offset, int limit) {
        return jdbc.query("""
                SELECT q.*, a.rule_name, a.label, a.explanation, a.assessment_source,
                       a.flagged_at AS assessed_at, a.flagged_date AS assessment_date FROM (
                    SELECT t.transaction_id, t.wallet_id, t.transaction_amount_paise, t.transaction_status,
                           'HELD' AS kind, h.rule_code, h.reason, h.held_at AS queued_at
                    FROM transactions t JOIN transfer_holds h ON h.transaction_id = t.transaction_id
                    WHERE t.transaction_status = 'HELD'
                    UNION ALL
                    SELECT t.transaction_id, t.wallet_id, t.transaction_amount_paise, t.transaction_status,
                           'FLAGGED' AS kind, f.rule_code, f.reason, f.flagged_at AS queued_at
                    FROM transactions t JOIN transfer_flags f ON f.transaction_id = t.transaction_id
                    WHERE t.transaction_status = 'POSTED'
                ) q
                LEFT JOIN risk_flags a ON a.transaction_id = q.transaction_id
                WHERE NOT EXISTS (SELECT 1 FROM transfer_risk_decisions d WHERE d.transaction_id = q.transaction_id)
                ORDER BY q.queued_at DESC, q.transaction_id DESC LIMIT :limit OFFSET :offset
                """, Map.of("offset", offset, "limit", limit), (row, index) -> {
            Timestamp assessedAt = row.getTimestamp("assessed_at");
            Assessment assessment = row.getString("label") == null ? null
                    : new Assessment(row.getString("label"), row.getString("explanation"), row.getString("assessment_source"));
            return new QueueItem(
                row.getLong("transaction_id"), row.getLong("wallet_id"), row.getLong("transaction_amount_paise"),
                row.getString("transaction_status"), row.getString("kind"), row.getString("rule_code"),
                row.getString("reason"), row.getTimestamp("queued_at").toInstant(), row.getString("rule_name"),
                assessment, assessedAt == null ? null : assessedAt.toInstant(),
                row.getObject("assessment_date", java.time.LocalDate.class));
        });
    }
}
