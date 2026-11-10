package com.airtribe.wallet.repository;

import java.math.BigDecimal;
import java.util.Map;

public interface RiskRuleRepository {
    record RiskRule(String ruleCode, String ruleName, Integer lookbackMinutes, BigDecimal threshold, boolean enabled) { }

    // Keyed by rule name; one statement gives a transfer one settings snapshot.
    Map<String, RiskRule> findAll();
}
