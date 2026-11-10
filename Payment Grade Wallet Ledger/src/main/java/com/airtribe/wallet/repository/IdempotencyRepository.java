package com.airtribe.wallet.repository;

import com.airtribe.wallet.service.StoredHttpResponse;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository {
    enum Status { IN_PROGRESS, COMPLETED }

    record SavedRequest(byte[] requestHash, Status status, StoredHttpResponse response) {
        public SavedRequest { requestHash = requestHash.clone(); }
        @Override public byte[] requestHash() { return requestHash.clone(); }
    }

    // Execute/flush this INSERT immediately. UNIQUE(user_id, key) waits normally;
    // translate only that constraint's violation to DuplicateIdempotencyKeyException.
    // Do not commit a separate claim or configure a custom lock timeout.
    void insert(long userId, UUID key, byte[] requestHash);

    Optional<SavedRequest> findByUserAndKey(long userId, UUID key);

    // Store exact response text/code, transition to COMPLETED, and require one updated row.
    void complete(long userId, UUID key, StoredHttpResponse response);
}
