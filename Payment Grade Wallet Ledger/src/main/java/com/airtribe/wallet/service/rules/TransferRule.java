package com.airtribe.wallet.service.rules;

import com.airtribe.wallet.dto.RuleModels.RuleContext;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import com.airtribe.wallet.repository.RiskRuleRepository.RiskRule;

public interface TransferRule {
    String code();

    String name();

    RuleResult evaluate(RuleContext context, RiskRule configuration);
}
