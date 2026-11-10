package com.airtribe.wallet.service.rules;

import com.airtribe.wallet.dto.RuleModels.Action;
import com.airtribe.wallet.dto.RuleModels.RuleContext;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import com.airtribe.wallet.repository.TransferRepository;
import com.airtribe.wallet.repository.RiskRuleRepository.RiskRule;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class TransactionVelocityRule implements TransferRule {
    public static final String CODE = "TRANSFER_VELOCITY_EXCEEDED";
    public static final String NAME = "Velocity";

    private final TransferRepository transfers;

    public TransactionVelocityRule(TransferRepository transfers) {
        this.transfers = transfers;
    }

    @Override
    public String code() { return CODE; }

    @Override
    public String name() { return NAME; }

    @Override
    public RuleResult evaluate(RuleContext context, RiskRule configuration) {
        long threshold = configuration.threshold().longValueExact();
        long count = transfers.countPostedOutgoingTransfers(context.senderWalletId(), configuration.lookbackMinutes());
        if (count > threshold) {
            return new RuleResult(Action.HOLD, code(), name(),
                    "Outgoing transfer count " + count + " exceeds " + threshold
                            + " in the last " + configuration.lookbackMinutes() + " minutes.");
        }
        return RuleResult.allow(code(), name());
    }
}
