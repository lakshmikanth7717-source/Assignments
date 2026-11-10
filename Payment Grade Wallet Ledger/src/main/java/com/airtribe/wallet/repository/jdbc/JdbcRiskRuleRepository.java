package com.airtribe.wallet.repository.jdbc;

import com.airtribe.wallet.repository.RiskRuleRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcRiskRuleRepository implements RiskRuleRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcRiskRuleRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Map<String, RiskRule> findAll() {
        return jdbc.query("""
                SELECT rule_code, rule_name, lookback_minutes, threshold, enabled FROM risk_rules
                """, Map.of(), rows -> {
            Map<String, RiskRule> rules = new LinkedHashMap<>();
            while (rows.next()) {
                RiskRule rule = new RiskRule(rows.getString("rule_code"), rows.getString("rule_name"),
                        rows.getObject("lookback_minutes", Integer.class),
                        rows.getBigDecimal("threshold"), rows.getBoolean("enabled"));
                rules.put(rule.ruleName(), rule);
            }
            return Map.copyOf(rules);
        });
    }
}
