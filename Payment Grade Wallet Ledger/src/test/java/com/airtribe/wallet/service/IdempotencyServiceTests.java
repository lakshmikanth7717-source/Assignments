package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.TransferModels.MoneyMovementRequest;
import com.airtribe.wallet.dto.TransferModels.Recipient;
import com.airtribe.wallet.dto.TransferModels.TransferRequest;
import com.airtribe.wallet.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(60)
class IdempotencyServiceTests extends ServiceIntegrationTestSupport {
    @Test
    void sameKeyCannotReplayATopUpAsAWithdrawalWithAnIdenticalBody() {
        WalletPair pair = createPair(100_000L);
        UUID key = UUID.randomUUID();
        MoneyMovementRequest request = new MoneyMovementRequest(pair.senderId(), 500L);
        StoredHttpResponse original = transfers.topUp(pair.userId(), key, request);
        assertThatThrownBy(() -> transfers.withdraw(pair.userId(), key, request))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThat(balance(pair.senderId())).isEqualTo(100_500L);
        assertThat(transfers.topUp(pair.userId(), key, request)).isEqualTo(original);
    }

    @Test
    void sameKeyCannotReplayAReversalForADifferentTransaction() throws Exception {
        WalletPair pair = createPair(100_000L);
        var first = transfers.transfer(pair.userId(), UUID.randomUUID(), request(pair, 1_000L));
        var second = transfers.transfer(pair.userId(), UUID.randomUUID(), request(pair, 1_000L));
        long firstId = json.readTree(first.body()).path("transactionId").asLong();
        long secondId = json.readTree(second.body()).path("transactionId").asLong();
        UUID key = UUID.randomUUID();
        var original = transfers.reverse(pair.userId(), firstId, key);
        assertThatThrownBy(() -> transfers.reverse(pair.userId(), secondId, key))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        assertThat(transfers.reverse(pair.userId(), firstId, key)).isEqualTo(original);
        assertThat(balance(pair.senderId())).isEqualTo(99_000L);
    }

    @Test
    void sameKeyAndPayloadReplayTheExactCompletedResponse() {
        WalletPair pair = createPair(100_000L);
        UUID key = UUID.randomUUID();
        StoredHttpResponse original = transfers.transfer(pair.userId(), key, request(pair, 8_000L));

        for (int i = 0; i < 3; i++) {
            // Reconstruct the DTO: replay depends on request content, not object identity.
            assertThat(transfers.transfer(pair.userId(), key, request(pair, 8_000L))).isEqualTo(original);
        }

        assertThat(original.statusCode()).isEqualTo(201);
        assertCompletedKey(pair.userId(), key, original);
        assertFinancialState(pair, 8_000L, 1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejectedWithoutChangingTheOriginalResult() {
        WalletPair pair = createPair(100_000L);
        UUID key = UUID.randomUUID();
        StoredHttpResponse original = transfers.transfer(pair.userId(), key, request(pair, 8_000L));

        assertThatThrownBy(() -> transfers.transfer(pair.userId(), key, request(pair, 4_000L)))
                .isInstanceOfSatisfying(ApiException.class, rejection -> {
                    assertThat(rejection.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rejection.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });

        assertThat(transfers.transfer(pair.userId(), key, request(pair, 8_000L))).isEqualTo(original);
        assertCompletedKey(pair.userId(), key, original);
        assertFinancialState(pair, 8_000L, 1);
    }

    @Test
    void concurrentRetriesWithTheSameKeyPostOnceAndAllReplayTheSameResponse() throws Exception {
        WalletPair pair = createPair(100_000L);
        UUID key = UUID.randomUUID();
        TransferRequest request = request(pair, 8_000L);
        List<Callable<StoredHttpResponse>> calls = new ArrayList<>();
        for (int i = 0; i < CLIENTS; i++) {
            calls.add(() -> transfers.transfer(pair.userId(), key, request));
        }

        // The winner is blocked on the wallet while the other requests wait on its key claim.
        List<StoredHttpResponse> responses = callConcurrentlyWhileWalletLocked(pair.senderId(), calls);

        assertThat(responses).hasSize(CLIENTS);
        StoredHttpResponse original = responses.get(0);
        assertThat(original.statusCode()).isEqualTo(201);
        assertThat(responses).containsOnly(original);
        assertCompletedKey(pair.userId(), key, original);
        assertFinancialState(pair, 8_000L, 1);
    }

    @Test
    void theSameKeyCanBeUsedIndependentlyByDifferentUsers() {
        WalletPair first = createPair(100_000L);
        WalletPair second = createPair(100_000L);
        UUID sharedKey = UUID.randomUUID();

        StoredHttpResponse firstResponse = transfers.transfer(first.userId(), sharedKey, request(first, 8_000L));
        StoredHttpResponse secondResponse = transfers.transfer(second.userId(), sharedKey, request(second, 8_000L));

        assertThat(firstResponse.statusCode()).isEqualTo(201);
        assertThat(secondResponse.statusCode()).isEqualTo(201);
        assertThat(firstResponse.body()).isNotEqualTo(secondResponse.body());
        assertCompletedKey(first.userId(), sharedKey, firstResponse);
        assertCompletedKey(second.userId(), sharedKey, secondResponse);
        assertThat(transfers.transfer(first.userId(), sharedKey, request(first, 8_000L))).isEqualTo(firstResponse);
        assertThat(transfers.transfer(second.userId(), sharedKey, request(second, 8_000L))).isEqualTo(secondResponse);
        assertFinancialState(first, 8_000L, 1);
        assertFinancialState(second, 8_000L, 1);
    }

    @Test
    void differentKeysWithTheSamePayloadCreateDistinctTransfers() {
        WalletPair pair = createPair(100_000L);
        UUID firstKey = UUID.randomUUID();
        UUID secondKey = UUID.randomUUID();
        TransferRequest request = request(pair, 8_000L);

        StoredHttpResponse first = transfers.transfer(pair.userId(), firstKey, request);
        StoredHttpResponse second = transfers.transfer(pair.userId(), secondKey, request);

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(first.body()).isNotEqualTo(second.body());
        assertCompletedKey(pair.userId(), firstKey, first);
        assertCompletedKey(pair.userId(), secondKey, second);
        assertFinancialState(pair, 16_000L, 2);
    }

    @Test
    void insufficientFundsRollBackTheKeySoTheRequestCanBeRetriedAfterFunding() {
        WalletPair pair = createPair(1_000L);
        UUID key = UUID.randomUUID();
        TransferRequest request = request(pair, 8_000L);

        assertThatThrownBy(() -> transfers.transfer(pair.userId(), key, request))
                .isInstanceOfSatisfying(ApiException.class, rejection -> {
                    assertThat(rejection.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(rejection.code()).isEqualTo("INSUFFICIENT_FUNDS");
                });
        assertThat(keyCount(pair.userId(), key)).isZero();
        assertFinancialState(pair, 0, 0);

        assertThat(transfers.topUp(pair.userId(), UUID.randomUUID(),
                new MoneyMovementRequest(pair.senderId(), 7_000L)).statusCode()).isEqualTo(201);
        StoredHttpResponse retry = transfers.transfer(pair.userId(), key, request);

        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(transfers.transfer(pair.userId(), key, request)).isEqualTo(retry);
        assertCompletedKey(pair.userId(), key, retry);
        WalletPair funded = new WalletPair(pair.userId(), pair.senderId(), pair.recipientId(), 8_000L);
        assertFinancialState(funded, 8_000L, 1);
    }

    @Test
    void unexpectedFailureRollsBackFinancialChangesAndTheKeyBeforeRetry() {
        WalletPair pair = createPair(100_000L);
        UUID key = UUID.randomUUID();
        TransferRequest request = request(pair, 8_000L);
        IllegalStateException failure = new IllegalStateException("Simulated failure after a wallet debit");

        // Exercise IdempotencyService's transaction directly with a failure after a real write.
        assertThatThrownBy(() -> idempotency.execute(pair.userId(), key, request, () -> {
            jdbc.update("UPDATE wallets SET balance = balance - ? WHERE wallet_id = ?", 8_000L, pair.senderId());
            assertThat(balance(pair.senderId())).isEqualTo(92_000L);
            throw failure;
        })).isSameAs(failure);

        assertThat(keyCount(pair.userId(), key)).isZero();
        assertFinancialState(pair, 0, 0);
        StoredHttpResponse retry = transfers.transfer(pair.userId(), key, request);
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(transfers.transfer(pair.userId(), key, request)).isEqualTo(retry);
        assertCompletedKey(pair.userId(), key, retry);
        assertFinancialState(pair, 8_000L, 1);
    }

    private WalletPair createPair(long openingBalance) {
        long userId = createUser("sender");
        long recipientUserId = createUser("recipient");
        long senderId = createWallet(userId, "Sender");
        long recipientId = createWallet(recipientUserId, "Recipient");
        assertThat(transfers.topUp(userId, UUID.randomUUID(),
                new MoneyMovementRequest(senderId, openingBalance)).statusCode()).isEqualTo(201);
        jdbc.update("UPDATE risk_rules SET enabled = false");
        return new WalletPair(userId, senderId, recipientId, openingBalance);
    }

    private TransferRequest request(WalletPair pair, long amount) {
        return new TransferRequest(pair.senderId(), List.of(new Recipient(pair.recipientId(), amount)));
    }

    private long keyCount(long userId, UUID key) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM idempotency_keys WHERE user_id = ? AND key = ?
                """, Long.class, userId, key);
    }

    private void assertCompletedKey(long userId, UUID key, StoredHttpResponse response) {
        assertThat(keyCount(userId, key)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT status::text FROM idempotency_keys WHERE user_id = ? AND key = ?
                """, String.class, userId, key)).isEqualTo("COMPLETED");
        StoredHttpResponse saved = jdbc.queryForObject("""
                SELECT response_code, response_body FROM idempotency_keys WHERE user_id = ? AND key = ?
                """, (row, index) -> new StoredHttpResponse(row.getInt("response_code"), row.getString("response_body")),
                userId, key);
        assertThat(saved).isEqualTo(response);
    }

    private void assertFinancialState(WalletPair pair, long transferred, long transferCount) {
        assertThat(balance(pair.senderId())).isEqualTo(pair.openingBalance() - transferred);
        assertThat(balance(pair.recipientId())).isEqualTo(transferred);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND transaction_type = 'TRANSFER'
                """, Long.class, pair.senderId())).isEqualTo(transferCount);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions WHERE wallet_id = ? AND transaction_type = 'TRANSFER'
                  AND transaction_status <> 'POSTED'
                """, Long.class, pair.senderId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_entries e JOIN transactions t USING (transaction_id)
                WHERE t.wallet_id = ? AND t.transaction_type = 'TRANSFER'
                """, Long.class, pair.senderId())).isEqualTo(2 * transferCount);
        assertThat(jdbc.queryForList("""
                SELECT t.transaction_id FROM transactions t
                LEFT JOIN ledger_entries e USING (transaction_id)
                WHERE t.wallet_id = ? AND t.transaction_type = 'TRANSFER'
                GROUP BY t.transaction_id
                HAVING COUNT(e.ledger_entry_id) <> 2 OR SUM(e.amount_paise) <> 0
                """, Long.class, pair.senderId())).isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT w.wallet_id FROM wallets w
                LEFT JOIN (
                    SELECT e.wallet_id, SUM(e.amount_paise) AS balance FROM ledger_entries e
                    JOIN transactions t USING (transaction_id)
                    WHERE t.transaction_status = 'POSTED' GROUP BY e.wallet_id
                ) posted ON posted.wallet_id = w.wallet_id
                WHERE w.wallet_id IN (?, ?) AND w.balance <> COALESCE(posted.balance, 0)
                """, Long.class, pair.senderId(), pair.recipientId())).isEmpty();
    }

    private record WalletPair(long userId, long senderId, long recipientId, long openingBalance) { }
}
