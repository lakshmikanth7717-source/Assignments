package com.airtribe.wallet.repository;

import com.airtribe.wallet.dto.RiskModels.*;
import com.airtribe.wallet.dto.RuleModels.RuleResult;
import java.util.List;

public interface RiskRepository {
    void flag(long transactionId, RuleResult result);
    void saveAssessment(long transactionId, RuleResult result, Assessment assessment);
    boolean isFlagged(long transactionId);
    boolean hasDecision(long transactionId);
    void recordDecision(long transactionId, long adminId, DecisionRequest request);
    List<QueueItem> findQueue(long offset, int limit);
}
