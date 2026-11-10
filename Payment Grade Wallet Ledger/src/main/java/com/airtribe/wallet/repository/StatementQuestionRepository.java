package com.airtribe.wallet.repository;

import com.airtribe.wallet.dto.StatementQuestionModels.Plan;
import com.airtribe.wallet.dto.StatementQuestionModels.QueryResult;

public interface StatementQuestionRepository {
    // callerId comes from the authenticated principal, never the model or question text.
    QueryResult query(long callerId, Plan plan);
}
