package service;


import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class BookingService {

    private final RedissonClient redissonClient;
    private final BookingTransactionService bookingTransactions;

    public BookingService(RedissonClient redissonClient, BookingTransactionService bookingTransactions) {
        this.redissonClient = redissonClient;
        this.bookingTransactions = bookingTransactions;
    }

    public BookingResult holdSeatForFiveMinutes(Long seatId, Long userId) {
        // Step 1: Create a unique Redis lock key for the seat
        String lockKey = "lock:seat:" + seatId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Step 2: Try to acquire the Redis lock
            // Keep the watchdog active while the database operation runs.
            boolean isLockAcquired = lock.tryLock(10, TimeUnit.SECONDS);

            if (!isLockAcquired) {
                return BookingResult.failed("System is busy, please try again.");
            }

            // Step 3: Start database processing only after acquiring the lock
            // With no outer transaction, the separate bean's proxy commits before returning.
            return bookingTransactions.holdSeat(seatId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BookingResult.failed("Transaction was interrupted.");
        } finally {
            // Step 6: Release the Redis lock after all processes are completed
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Idempotent Payment Webhook Handler
     *
     * @param idempotencyKey Unique event ID or transaction ID from the payment gateway (e.g., evt_998877)
     * @param bookingId      The associated booking ID
     * @param gatewayTxnId   The gateway's transaction reference
     * @param amount         Total amount paid
     */
    public WebhookResult processPaymentWebhookIdempotently(String idempotencyKey,
                                                           String bookingId,
                                                           String gatewayTxnId,
                                                           Double amount)
    {
        try
        {
            return bookingTransactions.processPayment(idempotencyKey, bookingId, gatewayTxnId, amount);
        } catch (RuntimeException e) {
            // A new transaction rolls back here; a joined transaction is marked rollback-only.
            return WebhookResult.failed("Error processing payment webhook: " + e.getMessage());
        }
    }
}
