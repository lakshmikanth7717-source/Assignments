package com.airtribe.wallet.service;

import com.airtribe.wallet.dto.WalletModels.*;
import com.airtribe.wallet.exception.ApiException;
import com.airtribe.wallet.repository.LedgerRepository;
import com.airtribe.wallet.repository.UserRepository;
import com.airtribe.wallet.repository.WalletRepository;
import com.airtribe.wallet.repository.WalletRepository.Wallet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerRepository ledger;
    private final IdempotencyService idempotency;

    public WalletService(WalletRepository wallets, UserRepository users, LedgerRepository ledger,
                         IdempotencyService idempotency) {
        this.wallets = wallets;
        this.users = users;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    public StoredHttpResponse openWallet(long actorUserId, long userId, UUID key, OpenWalletRequest request) {
        if (actorUserId != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WALLET_ACCESS_DENIED",
                    "You can only open wallets for your own user account.");
        }
        return idempotency.execute(actorUserId, key, "OPEN_WALLET/" + userId, request, () -> {
            if (!users.existsById(userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found.");
            }
            Wallet wallet = wallets.create(userId, request.walletName().trim());
            return idempotency.created(new WalletResponse(wallet.walletId(), wallet.userId(), wallet.walletName()));
        });
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public BalanceResponse getBalance(long actorUserId, long walletId) {
        Wallet wallet = requireOwnedWallet(actorUserId, walletId);
        return new BalanceResponse(walletId, wallet.balancePaise(), "INR");
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public StatementResponse getStatement(long actorUserId, long walletId, int page, int size,
                                          Long offset, String pagination, String cursor) {
        requireOwnedWallet(actorUserId, walletId);
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION",
                    "Page must be nonnegative and size must be between 1 and 100.");
        }
        if (!pagination.equals("offset") && !pagination.equals("cursor")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Pagination must be offset or cursor.");
        }
        boolean useCursor = pagination.equals("cursor") || cursor != null;
        if ((offset != null && (offset < 0 || page != 0)) || (useCursor && (page != 0 || offset != null))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Use either page, offset, or cursor pagination.");
        }
        long effectiveOffset = offset == null ? (long) page * size : offset;
        List<StatementEntry> entries;
        if (useCursor) {
            StatementCursor boundary = cursor == null ? null : decodeCursor(cursor, walletId);
                entries = ledger.findStatementAfter(walletId, boundary == null ? null : boundary.createdAt(),
                    boundary == null ? null : boundary.entryId(), size + 1);
        } else {
            entries = ledger.findStatement(walletId, effectiveOffset, size + 1);
        }
        boolean hasMore = entries.size() > size;
        List<StatementEntry> visible = hasMore ? entries.subList(0, size) : entries;
        String next = hasMore ? encodeCursor(walletId, visible.get(visible.size() - 1)) : null;
        return new StatementResponse(walletId, "INR", visible, page, size, hasMore, useCursor ? 0 : effectiveOffset, next);
    }

    private record StatementCursor(long walletId, Instant createdAt, long entryId) { }

    private static String encodeCursor(long walletId, StatementEntry entry) {
        String value = walletId + "|" + entry.createdAt() + "|" + entry.ledgerEntryId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static StatementCursor decodeCursor(String value, long walletId) {
        try {
            if (value.length() > 300) { throw new IllegalArgumentException(); }
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3) { throw new IllegalArgumentException(); }
            StatementCursor cursor = new StatementCursor(Long.parseLong(parts[0]), Instant.parse(parts[1]), Long.parseLong(parts[2]));
            if (cursor.walletId() != walletId || cursor.entryId() <= 0) { throw new IllegalArgumentException(); }
            int year = cursor.createdAt().atOffset(java.time.ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9998) { throw new IllegalArgumentException(); }
            return cursor;
        } catch (RuntimeException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "The statement cursor is invalid for this wallet.");
        }
    }

    private Wallet requireOwnedWallet(long userId, long walletId) {
        Wallet wallet = wallets.findById(walletId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found."));
        if (wallet.userId() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WALLET_ACCESS_DENIED", "This wallet belongs to another user.");
        }
        return wallet;
    }
}
