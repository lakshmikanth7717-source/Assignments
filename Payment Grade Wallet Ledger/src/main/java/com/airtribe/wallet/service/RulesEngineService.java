package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.RuleModels.Action;
import com.airtribe.wallet.dto.RuleModels.RecentTransfer;
import com.airtribe.wallet.dto.RuleModels.RuleContext;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import com.airtribe.wallet.service.rules.TransferRule;
import com.airtribe.wallet.repository.RiskRuleRepository;
import com.airtribe.wallet.repository.RiskRuleRepository.RiskRule;
import com.airtribe.wallet.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RulesEngineService {
    private static final Logger log = LoggerFactory.getLogger(RulesEngineService.class);
    private final List<TransferRule> rules;
    private final RiskRuleRepository configurations;
    private final TransferRepository transfers;
    public RulesEngineService(List<TransferRule> rules, RiskRuleRepository configurations, TransferRepository transfers) {
        this.rules = List.copyOf(rules);
        this.configurations = configurations;
        this.transfers = transfers;
    }

    // The caller must hold the sender wallet lock in the posting transaction.
    public RuleResult evaluate(RuleContext context) {
        RuleResult result = evaluateRules(context);
        // One shared lookup even when HOLD short-circuits, all rules allow, or all are disabled.
        long historyStarted = System.nanoTime();
        List<RecentTransfer> history = transfers.findLast10OutgoingTransfers(context.senderWalletId());
        log.debug("risk_rule_recent_transfers senderWalletId={} count={} elapsedNanos={}",
                context.senderWalletId(), history.size(), System.nanoTime() - historyStarted);
        return result.withLast10Transfers(history);
    }

    private RuleResult evaluateRules(RuleContext context) {
        if (rules.isEmpty()) { return RuleResult.allow(); }
        long configurationStarted = System.nanoTime();
        Map<String, RiskRule> settings = configurations.findAll();
        log.debug("risk_rule_configurations elapsedNanos={}", System.nanoTime() - configurationStarted);
        // Missing configuration is an error, even if an earlier rule could return HOLD.
        for (TransferRule rule : rules) {
            RiskRule configuration = settings.get(rule.name());
            if (configuration == null) {
                throw new IllegalStateException("Missing risk rule configuration: " + rule.name());
            }
            if (!configuration.ruleCode().equals(rule.code())) {
                throw new IllegalStateException("Risk rule name/code mismatch: " + rule.name());
            }
        }
        RuleResult flag = null;
        for (TransferRule rule : rules) {
            RiskRule configuration = settings.get(rule.name());
            if (!configuration.enabled()) { continue; }
            long started = System.nanoTime();
            RuleResult result = rule.evaluate(context, configuration);
            long elapsedNanos = System.nanoTime() - started;
            log.debug("transfer_rule ruleName={} ruleCode={} action={} elapsedNanos={}",
                    result.ruleName(), result.ruleCode(), result.action(), elapsedNanos);
            if (result.action() == Action.HOLD) {
                return result;
            }
            if (result.action() == Action.FLAG) {
                log.warn("transfer_risk_flag ruleName={} ruleCode={} senderUserId={} senderWalletId={} totalAmountPaise={} flaggedRecipients={} thresholdPaise={}",
                        result.ruleName(), result.ruleCode(), context.senderUserId(), context.senderWalletId(), context.amountPaise(),
                        result.flaggedRecipients(), configuration.threshold().toPlainString());
                flag = result;
            }
        }
        return flag == null ? RuleResult.allow() : flag;
    }
}
