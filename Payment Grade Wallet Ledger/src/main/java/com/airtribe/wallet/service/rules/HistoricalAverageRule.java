package com.airtribe.wallet.service.rules;

import com.airtribe.wallet.dto.RuleModels.Action;
import com.airtribe.wallet.dto.RuleModels.RuleContext;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import com.airtribe.wallet.repository.RiskRuleRepository.RiskRule;
import com.airtribe.wallet.repository.TransferRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@Order(20)
public class HistoricalAverageRule implements TransferRule {
    public static final String CODE = "HISTORICAL_AVERAGE_EXCEEDED";
    public static final String NAME = "Amount Spike";

    private final TransferRepository transfers;

    public HistoricalAverageRule(TransferRepository transfers) { this.transfers = transfers; }

    @Override
    public String code() { return CODE; }

    @Override
    public String name() { return NAME; }

    @Override
    public RuleResult evaluate(RuleContext context, RiskRule configuration) {
        Optional<BigDecimal> average = transfers.averagePostedOutgoingTransfers(
                context.senderWalletId(), configuration.lookbackMinutes());
        BigDecimal limit = BigDecimal.valueOf(context.amountPaise()).multiply(configuration.threshold());
        // Intentionally compare historical average > multiplier * current amount.
        if (average.isPresent() && average.get().compareTo(limit) > 0) {
            return new RuleResult(Action.HOLD, code(), name(),
                    "Historical average " + average.get().toPlainString() + " paise exceeds "
                            + configuration.threshold().stripTrailingZeros().toPlainString()
                            + " times the current transfer amount over the last "
                            + configuration.lookbackMinutes() + " minutes.");
        }
        return RuleResult.allow(code(), name());
    }
}
