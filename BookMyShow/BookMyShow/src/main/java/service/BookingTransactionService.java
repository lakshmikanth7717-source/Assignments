package service;

import entity.Booking;
import entity.Payment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repo.BookingRepository;
import repo.PaymentRepository;
import repo.SeatRepository;

import java.time.LocalDateTime;

/**
 * Database operations called through Spring's proxy by BookingService.
 * Starts a transaction when needed, or participates in the caller's existing transaction.
 */
@Service
public class BookingTransactionService {
    private final SeatRepository seatRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    public BookingTransactionService(SeatRepository seatRepository, PaymentRepository paymentRepository,
                                     BookingRepository bookingRepository) {
        this.seatRepository = seatRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public BookingResult holdSeat(Long seatId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        int rowsUpdated = seatRepository.holdSeatIfAvailable(seatId, userId, now.plusMinutes(5), now);

        if (rowsUpdated == 0) {
            return BookingResult.failed("This seat is already held or booked by someone else.");
        }
        return BookingResult.success("Seat has been held for you for 5 minutes.");
    }

    @Transactional
    public WebhookResult processPayment(String idempotencyKey, String bookingId,
                                         String gatewayTxnId, Double amount) {
        // Serialize callbacks for the same booking before checking the payment ledger.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
        Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment != null) {
            if (!bookingId.equals(existingPayment.getBookingId())) {
                throw new IllegalArgumentException("Idempotency key belongs to another booking");
            }
            if ("SUCCESS".equals(existingPayment.getStatus())) {
                return WebhookResult.success("Webhook already processed successfully. Duplicate ignored.");
            } else if ("FAILED".equals(existingPayment.getStatus())) {
                return WebhookResult.success("Webhook previously marked as failed. Ignored.");
            }
        }

        if ("CONFIRMED".equals(booking.getStatus())) {
            return WebhookResult.success("Booking is already confirmed.");
        }

        if (existingPayment == null) {
            existingPayment = new Payment();
            existingPayment.setBookingId(bookingId);
            existingPayment.setIdempotencyKey(idempotencyKey);
            existingPayment.setAmount(amount);
        }

        existingPayment.setGatewayTransactionId(gatewayTxnId);
        existingPayment.setStatus("SUCCESS");
        paymentRepository.save(existingPayment);

        booking.setStatus("CONFIRMED");
        bookingRepository.save(booking);
        seatRepository.confirmSeatsForBooking(bookingId);

        return WebhookResult.success("Payment processed successfully and seats confirmed.");
    }
}
