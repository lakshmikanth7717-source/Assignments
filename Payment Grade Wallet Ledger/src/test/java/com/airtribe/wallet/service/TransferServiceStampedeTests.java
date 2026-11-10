package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.TransferModels.MoneyMovementRequest;
import com.airtribe.wallet.dto.TransferModels.Recipient;
import com.airtribe.wallet.dto.TransferModels.TransferRequest;
import com.airtribe.wallet.dto.TransferModels.TransferResponse;
import com.airtribe.wallet.dto.TransferModels.TransferStatus;
import com.airtribe.wallet.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class TransferServiceStampedeTests extends ServiceIntegrationTestSupport {
    @Test
    @Timeout(60)
    void concurrentTransfersPostOnceAndRejectRemainingRequests() throws Exception {
        Stampede stampede = runStampede();

        List<StoredHttpResponse> successes = stampede.outcomes().stream().filter(outcome -> outcome.response() != null)
                .map(Outcome::response).toList();
        List<ApiException> rejections = stampede.outcomes().stream().filter(outcome -> outcome.rejection() != null)
                .map(Outcome::rejection).toList();
        assertThat(successes).hasSize(1);
        assertThat(rejections).hasSize(CLIENTS - 1).allSatisfy(rejection -> {
            assertThat(rejection.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(rejection.code()).isEqualTo("INSUFFICIENT_FUNDS");
        });
        StoredHttpResponse success = successes.get(0);
        assertThat(success.statusCode()).isEqualTo(201);
        TransferResponse posted = json.readValue(success.body(), TransferResponse.class);
        assertThat(posted.status()).isEqualTo(TransferStatus.POSTED);
        assertThat(posted.amountPaise()).isEqualTo(8_000L);
        assertThat(posted.currency()).isEqualTo("INR");
        assertThat(jdbc.queryForObject("""
                SELECT transaction_id FROM transactions WHERE wallet_id = ? AND transaction_type = 'TRANSFER'
                """, Long.class, stampede.senderId())).isEqualTo(posted.transactionId());
    }

    @Test
    @Timeout(60)
    void concurrentTransfersPreserveBalancesAndLedgerConsistency() throws Exception {
        Stampede stampede = runStampede();
        long senderId = stampede.senderId();
        long recipientId = stampede.recipientId();

        assertThat(balance(senderId)).isEqualTo(2_000L);
        assertThat(balance(recipientId)).isEqualTo(8_000L);
        assertThat(jdbc.queryForObject("""
                SELECT SUM(balance) FROM wallets WHERE wallet_id IN (?, ?)
                """, Long.class, senderId, recipientId)).isEqualTo(10_000L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM wallets WHERE wallet_id IN (?, ?) AND balance < 0
                """, Long.class, senderId, recipientId)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT w.wallet_id FROM wallets w
                LEFT JOIN (
                    SELECT e.wallet_id, SUM(e.amount_paise) AS balance FROM ledger_entries e
                    JOIN transactions t USING (transaction_id)
                    WHERE t.transaction_status = 'POSTED' GROUP BY e.wallet_id
                ) posted ON posted.wallet_id = w.wallet_id
                WHERE w.wallet_id IN (?, ?) AND w.balance <> COALESCE(posted.balance, 0)
                """, Long.class, senderId, recipientId)).isEmpty();
    }

    @Test
    @Timeout(60)
    void concurrentTransfersPersistOnlyOneBalancedTransfer() throws Exception {
        Stampede stampede = runStampede();
        long senderId = stampede.senderId();
        long recipientId = stampede.recipientId();

        // Only the funding top-up and the winning transfer may leave financial records.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions WHERE wallet_id = ?
                """, Long.class, senderId)).isEqualTo(2L);
        long transactionId = jdbc.queryForObject("""
                SELECT transaction_id FROM transactions WHERE wallet_id = ? AND transaction_type = 'TRANSFER'
                """, Long.class, senderId);
        assertThat(jdbc.queryForList("""
                SELECT transaction_status::text FROM transactions WHERE wallet_id = ?
                """, String.class, senderId))
                .containsOnly("POSTED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_entries e JOIN transactions t USING (transaction_id) WHERE t.wallet_id = ?
                """, Long.class, senderId)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_entries WHERE transaction_id = ?
                """, Long.class, transactionId)).isEqualTo(2L);
        Map<Long, Long> movements = jdbc.query("""
                SELECT wallet_id, amount_paise FROM ledger_entries WHERE transaction_id = ?
                """, resultSet -> {
            Map<Long, Long> entries = new HashMap<>();
            while (resultSet.next()) {
                entries.put(resultSet.getLong("wallet_id"), resultSet.getLong("amount_paise"));
            }
            return entries;
        }, transactionId);
        assertThat(movements).containsExactlyInAnyOrderEntriesOf(Map.of(senderId, -8_000L, recipientId, 8_000L));
        assertThat(jdbc.queryForObject("""
                SELECT SUM(e.amount_paise) FROM ledger_entries e JOIN transactions t USING (transaction_id)
                WHERE t.wallet_id = ?
                """, Long.class, senderId)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT e.transaction_id FROM ledger_entries e JOIN transactions t USING (transaction_id)
                WHERE t.wallet_id = ? GROUP BY e.transaction_id HAVING SUM(e.amount_paise) <> 0
                """, Long.class, senderId)).isEmpty();
    }

    private Stampede runStampede() throws Exception {
        // Each test gets new, committed users/wallets and runs its own 20-client burst.
        // Assertions are scoped to these fixtures, so no test relies on execution order or clears another's data.
        long senderUserId = createUser("sender");
        long recipientUserId = createUser("recipient");
        long senderId = createWallet(senderUserId, "Sender");
        long recipientId = createWallet(recipientUserId, "Recipient");
        assertThat(transfers.topUp(senderUserId, UUID.randomUUID(),
                new MoneyMovementRequest(senderId, 10_000L)).statusCode()).isEqualTo(201);
        jdbc.update("UPDATE risk_rules SET enabled = false");

        TransferRequest request = new TransferRequest(senderId, List.of(new Recipient(recipientId, 8_000L)));
        return new Stampede(senderId, recipientId, contendForSender(senderUserId, senderId, request));
    }

    private List<Outcome> contendForSender(long userId, long senderId, TransferRequest request) throws Exception {
        List<Callable<Outcome>> calls = new ArrayList<>();
        for (int i = 0; i < CLIENTS; i++) {
            UUID key = UUID.randomUUID();
            calls.add(() -> {
                try {
                    return new Outcome(transfers.transfer(userId, key, request), null);
                } catch (ApiException rejected) {
                    return new Outcome(null, rejected);
                }
            });
        }
        return callConcurrentlyWhileWalletLocked(senderId, calls);
    }

    private record Outcome(StoredHttpResponse response, ApiException rejection) { }

    private record Stampede(long senderId, long recipientId, List<Outcome> outcomes) { }
}
