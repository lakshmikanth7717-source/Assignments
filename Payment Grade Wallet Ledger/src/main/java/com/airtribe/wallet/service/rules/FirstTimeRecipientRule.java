package com.airtribe.wallet.service.rules;

import com.airtribe.wallet.dto.RuleModels.Action;
import com.airtribe.wallet.dto.RuleModels.RuleContext;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import com.airtribe.wallet.dto.TransferModels.Recipient;
import com.airtribe.wallet.repository.RiskRuleRepository.RiskRule;
import com.airtribe.wallet.repository.TransferRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Component
@Order(30)
public class FirstTimeRecipientRule implements TransferRule {
    public static final String CODE = "FIRST_TIME_RECIPIENT_HIGH_AMOUNT";
    public static final String NAME = "NewPayeeLargeAmount";

    private final TransferRepository transfers;

    public FirstTimeRecipientRule(TransferRepository transfers) { this.transfers = transfers; }

    @Override
    public String code() { return CODE; }

    @Override
    public String name() { return NAME; }

    @Override
    public RuleResult evaluate(RuleContext context, RiskRule configuration) {
        List<Recipient> candidates = context.recipients().stream()
                .filter(recipient -> BigDecimal.valueOf(recipient.amountPaise()).compareTo(configuration.threshold()) > 0)
                .toList();
        if (candidates.isEmpty()) { return RuleResult.allow(code(), name()); }
        Set<Long> known = transfers.findPreviouslyPaidRecipientWalletIds(context.senderUserId(),
                candidates.stream().map(Recipient::walletId).toList());
                                                                                                    List<Recipient> flagged = candidates.stream().filter(recipient -> !known.contains(recipient.walletId())).toList();
        if (flagged.isEmpty()) { return RuleResult.allow(code(), name()); }
        return new RuleResult(Action.FLAG, code(), name(),
                "First-time recipient amount exceeds " + configuration.threshold().stripTrailingZeros().toPlainString()
                        + " paise; log detection and continue transfer.", flagged);
    }
}
