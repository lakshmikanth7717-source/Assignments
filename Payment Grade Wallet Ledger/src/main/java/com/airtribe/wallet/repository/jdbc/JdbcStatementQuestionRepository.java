package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.dto.StatementQuestionModels.Plan;
import com.airtribe.wallet.dto.StatementQuestionModels.QueryResult;
import com.airtribe.wallet.repository.StatementQuestionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcStatementQuestionRepository implements StatementQuestionRepository {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String SENT_TO_PERSON = """
            WITH known_people AS (
                SELECT DISTINCT person.user_id
                FROM wallets sender JOIN transactions t ON t.wallet_id = sender.wallet_id
                JOIN ledger_entries e ON e.transaction_id = t.transaction_id AND e.amount_paise > 0
                JOIN wallets recipient ON recipient.wallet_id = e.wallet_id
                JOIN users person ON person.user_id = recipient.user_id
                WHERE sender.user_id = :callerId AND t.transaction_type = 'TRANSFER' AND t.transaction_status = 'POSTED'
                  AND lower(btrim(person.name)) = lower(:counterparty)
            ), contributions AS (
                SELECT t.transaction_id, SUM(e.amount_paise) AS amount
                FROM wallets sender JOIN transactions t ON t.wallet_id = sender.wallet_id
                JOIN ledger_entries e ON e.transaction_id = t.transaction_id AND e.amount_paise > 0
                JOIN wallets recipient ON recipient.wallet_id = e.wallet_id
                WHERE sender.user_id = :callerId AND recipient.user_id IN (SELECT user_id FROM known_people)
                  AND t.transaction_type = 'TRANSFER' AND t.transaction_status = 'POSTED'
                  AND t.posted_at >= :fromDate AND t.posted_at < :toDate
                GROUP BY t.transaction_id
            )
            SELECT COALESCE(SUM(amount), 0) AS value,
                   COALESCE(array_agg(transaction_id ORDER BY transaction_id), '{}'::bigint[]) AS transfer_ids,
                   (SELECT COUNT(*) FROM known_people) AS counterparty_matches
            FROM contributions
            """;
    private static final String OWN_TRANSFERS = """
            WITH contributions AS (
                SELECT t.transaction_id,
                       COALESCE(SUM(e.amount_paise) FILTER (WHERE e.amount_paise > 0), 0) AS received,
                       GREATEST(COALESCE(-SUM(e.amount_paise) FILTER (WHERE e.amount_paise < 0), 0),
                                COALESCE(SUM(e.amount_paise) FILTER (WHERE e.amount_paise > 0), 0)) AS amount
                FROM wallets own JOIN ledger_entries e ON e.wallet_id = own.wallet_id
                JOIN transactions t ON t.transaction_id = e.transaction_id
                WHERE own.user_id = :callerId AND t.transaction_type = 'TRANSFER' AND t.transaction_status = 'POSTED'
                  AND t.posted_at >= :fromDate AND t.posted_at < :toDate
                GROUP BY t.transaction_id
            )
            """;
    private static final String RECEIVED = OWN_TRANSFERS + """
            SELECT COALESCE(SUM(received), 0) AS value,
                   COALESCE(array_agg(transaction_id ORDER BY transaction_id), '{}'::bigint[]) AS transfer_ids,
                   0 AS counterparty_matches FROM contributions WHERE received > 0
            """;
    private static final String LARGEST = OWN_TRANSFERS + """
            SELECT COALESCE(MAX(amount), 0) AS value,
                   COALESCE(array_agg(transaction_id ORDER BY transaction_id), '{}'::bigint[]) AS transfer_ids,
                   0 AS counterparty_matches FROM contributions WHERE amount = (SELECT MAX(amount) FROM contributions)
            """;
    private static final String COUNT = OWN_TRANSFERS + """
            SELECT COUNT(*)::numeric AS value,
                   COALESCE(array_agg(transaction_id ORDER BY transaction_id), '{}'::bigint[]) AS transfer_ids,
                   0 AS counterparty_matches FROM contributions
            """;

    private final NamedParameterJdbcTemplate jdbc;
    public JdbcStatementQuestionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public QueryResult                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  query(long callerId, Plan plan) {
        String statement = switch (plan.type()) {
            case 1 -> SENT_TO_PERSON;
            case 2 -> RECEIVED;
            case 3 -> LARGEST;
            case 4 -> COUNT;
            default -> throw new IllegalArgumentException("Unsupported statement question type");
        };
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("callerId", callerId)
                .addValue("counterparty", plan.counterparty())
                .addValue("fromDate", plan.from().atStartOfDay(ZONE).toOffsetDateTime())
                .addValue("toDate", plan.to().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        // Every query calculates the aggregate and its evidence IDs within one database snapshot.
        return jdbc.queryForObject(statement, parameters, (row, index) -> {
            Array ids = row.getArray("transfer_ids");
            try {
                List<Long> transferIds = Arrays.asList((Long[]) ids.getArray());
                return new QueryResult(row.getBigDecimal("value").toBigIntegerExact(), transferIds,
                        row.getLong("counterparty_matches"));
            } finally { ids.free(); }
        });
    }
}
