package service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final RedissonClient redisson;
    private final BookingTransactionService database;
    private final PaymentWebhookVerifier verifier;

    public BookingService(RedissonClient redisson, BookingTransactionService database, PaymentWebhookVerifier verifier) {
        this.redisson = redisson;
        this.database = database;
        this.verifier = verifier;
    }

    public BookingResult holdSeatForFiveMinutes(Long showId, Long seatId, Long userId) {
        if (seatId == null) { return BookingResult.failed("Seat ID is required"); }
        return holdSeatsForFiveMinutes(showId, List.of(seatId), userId);
    }

    public BookingResult holdSeatsForFiveMinutes(Long showId, List<Long> seatIds, Long userId) {
        if (showId == null || showId <= 0 || userId == null || userId <= 0 || seatIds == null ||
                seatIds.isEmpty() || seatIds.stream().anyMatch(id -> id == null || id <= 0) ||
                seatIds.stream().distinct().count() != seatIds.size()) {
            return BookingResult.failed("A valid show, user and distinct seat IDs are required");
        }
        List<RLock> acquired = new ArrayList<>();
        try {
            // Consistent lock order avoids deadlocks between overlapping multi-seat requests.
            for (Long seatId : seatIds.stream().sorted().toList()) {
                RLock lock = redisson.getLock("lock:show:" + showId + ":seat:" + seatId);
                if (!lock.tryLock(10, TimeUnit.SECONDS)) {
                    return BookingResult.failed("Seats are busy; please retry");
                }
                acquired.add(lock);
            }
            // With no caller transaction, commit completes before these Redis locks are released.
            return database.holdSeats(showId, seatIds.stream().sorted().toList(), userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BookingResult.failed("Booking was interrupted");
        } catch (BookingException e) {
            return BookingResult.failed(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Seat reservation failed", e);
            return BookingResult.failed("Seat reservation could not be completed; please retry");
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                try {
                    RLock lock = acquired.get(i);
                    if (lock.isHeldByCurrentThread()) { lock.unlock(); }
                } catch (RuntimeException e) {
                    log.warn("Could not release a seat lock", e);
                }
            }
        }
    }

    /** Verifies a provider-neutral HMAC-SHA256 signature over the exact JSON bytes. */
    public WebhookResult processPaymentWebhookIdempotently(String rawJson, String signature) {
        try {
            return database.processPayment(verifier.verify(rawJson, signature));
        } catch (BookingException e) {
            return WebhookResult.failed(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Cross-booking event/transaction collisions cannot commit both payments.
            // Do not attempt recovery queries inside a failed or rollback-only transaction.
            log.warn("Conflicting payment reference", e);
            return WebhookResult.failed("Payment reference conflicts with an existing event");
        } catch (RuntimeException e) {
            log.error("Payment webhook processing failed", e);
            return WebhookResult.failed("Payment could not be processed; please retry");
        }
    }

    public BookingResult cancelBooking(String bookingId, Long userId) {
        try {
            return database.cancelBooking(bookingId, userId);
        } catch (BookingException e) {
            return BookingResult.failed(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Booking cancellation failed", e);
            return BookingResult.failed("Booking cancellation could not be completed; please retry");
        }
    }
}
