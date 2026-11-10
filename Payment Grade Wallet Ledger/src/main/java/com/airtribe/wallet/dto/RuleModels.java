package com.airtribe.wallet.dto;

import com.airtribe.wallet.dto.TransferModels.Recipient;
import com.airtribe.wallet.dto.TransferModels.TransferStatus;

import java.time.Instant;
import java.util.List;

public final class RuleModels {
    private RuleModels() { }

    public enum Action { ALLOW, HOLD, FLAG }

    public record RuleContext(long senderUserId, long senderWalletId, long amountPaise, List<Recipient> recipients) {
        public RuleContext { recipients = List.copyOf(recipients); }
    }

    public record RecentTransfer(long transactionId, long senderWalletId, long initiatedByUserId,
                                 long amountPaise, TransferStatus status, Instant createdAt) { }

    public record RuleResult(Action action, String ruleCode, String ruleName, String reason,
                             List<Recipient> flaggedRecipients, List<RecentTransfer> last10Transfers) {
        public RuleResult {
            flaggedRecipients = List.copyOf(flaggedRecipients);
            last10Transfers = List.copyOf(last10Transfers);
        }

        public RuleResult(Action action, String ruleCode, String ruleName, String reason,
                          List<Recipient> flaggedRecipients) {
            this(action, ruleCode, ruleName, reason, flaggedRecipients, List.of());
        }

        public RuleResult(Action action, String ruleCode, String ruleName, String reason) {
            this(action, ruleCode, ruleName, reason, List.of());
        }

        public RuleResult withLast10Transfers(List<RecentTransfer> transfers) {
            return new RuleResult(action, ruleCode, ruleName, reason, flaggedRecipients, transfers);
        }

        public static RuleResult allow(String ruleCode, String ruleName) {
            return new RuleResult(Action.ALLOW, ruleCode, ruleName, null);
        }

        // Aggregate ALLOW has no triggering rule; individual rules use the named overload.
        public static RuleResult allow() {
            return allow(null, null);
        }
    }
}
