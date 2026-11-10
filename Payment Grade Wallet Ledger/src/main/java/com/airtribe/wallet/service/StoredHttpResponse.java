package com.airtribe.wallet.service;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

/** Serialized JSON is returned verbatim, including when replaying a saved response. */
public record StoredHttpResponse(int statusCode, String body) {
    public StoredHttpResponse {
        if (statusCode < 200 || statusCode > 599) {
            throw new IllegalArgumentException("Expected a final HTTP response status");
        }
        Objects.requireNonNull(body, "body");
    }

    public ResponseEntity<String> toResponseEntity() {
        return ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
