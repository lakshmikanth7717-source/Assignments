package com.airtribe.wallet.security;

import com.airtribe.wallet.exception.ApiException;

import org.springframework.http.HttpStatus;

import java.security.Principal;

public final class CurrentUser {
    private CurrentUser() { }

    // Authentication will supply a trusted principal whose name is the user ID.
    // A user ID from a JSON body or an arbitrary client header is never used here.
    public static long id(Principal principal) {
        if (principal != null) {
            try {
                long id = Long.parseLong(principal.getName());
                if (id > 0) { return id; }
            } catch (NumberFormatException ignored) {
                // Treat an unrecognized identity as unauthenticated.
            }
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
    }
}
