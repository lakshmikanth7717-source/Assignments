package com.airtribe.wallet.service;

import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.repository.DuplicateIdempotencyKeyException;
import com.airtribe.wallet.repository.IdempotencyRepository;
import com.airtribe.wallet.repository.IdempotencyRepository.SavedRequest;
import com.airtribe.wallet.repository.IdempotencyRepository.Status;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final IdempotencyRepository keys;
    private final ObjectMapper responseJson;
    private final ObjectMapper requestJson;
    private final TransactionTemplate transactions;

    public IdempotencyService(IdempotencyRepository keys, ObjectMapper json,
                              PlatformTransactionManager transactionManager) {
        this.keys = keys;
        this.responseJson = json;
        this.requestJson = json.copy();
        this.requestJson.setConfig(this.requestJson.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // Own the transaction so the duplicate lookup starts after the failed claim rolls back.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public StoredHttpResponse execute(long userId, UUID key, Object request,
                                      Supplier<StoredHttpResponse> action) {
        return execute(userId, key, "UNSCOPED", request, action);
    }

    public StoredHttpResponse execute(long userId, UUID key, String operation, Object request,
                                      Supplier<StoredHttpResponse> action) {
        Objects.requireNonNull(key, "idempotencyKey");
        byte[] hash = requestHash(new RequestFingerprint(operation, request));
        try {
            return inTransaction(() -> {
                keys.insert(userId, key, hash);
                StoredHttpResponse response = action.get();
                keys.complete(userId, key, response);
                return response;
            });
        } catch (DuplicateIdempotencyKeyException duplicate) {
            // The failed transaction has already rolled back. Never read from it.
            return inTransaction(() -> replay(keys.findByUserAndKey(userId, key)
                    .orElseThrow(() -> new IllegalStateException("Conflicting idempotency key was not found")), hash));
        }
    }

    public record RequestFingerprint(String operation, Object body) { }

    public StoredHttpResponse inTransaction(Supplier<StoredHttpResponse> action) {
        return Objects.requireNonNull(transactions.execute(status -> action.get()));
    }

    public byte[] requestHash(Object request) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            byte[] body = request == null ? new byte[0] : requestJson.writeValueAsBytes(request);
            return hash.digest(body);
        } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Could not fingerprint the request", failure);
        }
    }

    public StoredHttpResponse replay(SavedRequest saved, byte[] requestHash) {
        if (!MessageDigest.isEqual(saved.requestHash(), requestHash)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used for a different request.");
        }
        if (saved.status() != Status.COMPLETED || saved.response() == null) {
            // This workflow never commits IN_PROGRESS on its own.
            throw new IllegalStateException("The committed idempotency record has no completed response");
        }
        return saved.response();
    }

    public StoredHttpResponse created(Object body) {
        return response(HttpStatus.CREATED, body);
    }

    public StoredHttpResponse accepted(Object body) {
        return response(HttpStatus.ACCEPTED, body);
    }

    public StoredHttpResponse ok(Object body) {
        return response(HttpStatus.OK, body);
    }

    private StoredHttpResponse response(HttpStatus status, Object body) {
        try {
            return new StoredHttpResponse(status.value(), responseJson.writeValueAsString(body));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialize the response", failure);
        }
    }
}
