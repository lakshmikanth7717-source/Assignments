package service;

import entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repo.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Called through Spring's proxy; plain @Transactional starts or joins a transaction. */
@Service
public class BookingTransactionService {
    private final ShowRepository shows;
    private final SeatRepository seats;
    private final ShowSeatRepository inventory;
    private final BookingRepository bookings;
    private final BookingItemRepository items;
    private final PaymentRepository payments;
    private final Clock clock;

    public BookingTransactionService(ShowRepository shows, SeatRepository seats, ShowSeatRepository inventory,
                                     BookingRepository bookings, BookingItemRepository items,
                                     PaymentRepository payments, Clock clock) {
        this.shows = shows;
        this.seats = seats;
        this.inventory = inventory;
        this.bookings = bookings;
        this.items = items;
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public BookingResult holdSeats(Long showId, List<Long> seatIds, Long userId) {
        if (showId == null || userId == null || userId <= 0 || seatIds == null || seatIds.isEmpty() ||
                seatIds.stream().anyMatch(id -> id == null || id <= 0) ||
                new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BookingException("A valid show, user and distinct seat IDs are required");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Show show = shows.findById(showId).orElseThrow(() -> new BookingException("Show not found"));
        if (show.getStatus() != ShowStatus.ACTIVE || !show.getStartTime().isAfter(now)) {
            throw new BookingException("Show is not open for booking");
        }
        List<Seat> layout = seats.findAllById(seatIds);
        if (layout.size() != seatIds.size() || layout.stream().anyMatch(s -> !Objects.equals(s.getScreenId(), show.getScreenId()))) {
            throw new BookingException("One or more seats do not belong to this show's screen");
        }
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID().toString());
        booking.setShowId(showId);
        booking.setUserId(userId);
        LocalDateTime expiry = now.plusMinutes(5);
        booking.setExpiresAt(expiry.isAfter(show.getStartTime()) ? show.getStartTime() : expiry);
        bookings.saveAndFlush(booking);

        BigDecimal total = BigDecimal.ZERO;
        for (Long seatId : seatIds.stream().sorted().toList()) {
            if (inventory.claim(showId, seatId, userId, booking.getId(), booking.getExpiresAt(), now) != 1) {
                throw new BookingException("One or more seats are unavailable");
            }
            ShowSeat slot = inventory.findById(new ShowSeatId(showId, seatId))
                    .orElseThrow(() -> new BookingException("Show seat not found"));
            BigDecimal price = Money.validate(slot.getPrice());
            BookingItem item = new BookingItem();
            item.setBookingId(booking.getId());
            item.setShowId(showId);
            item.setSeatId(seatId);
            item.setPrice(price);
            items.save(item);
            total = total.add(price);
        }
        booking.setTotalAmount(Money.validate(total));
        bookings.save(booking);
        return BookingResult.from(booking, "Seats held until " + booking.getExpiresAt());
    }

    @Transactional
    public WebhookResult processPayment(PaymentWebhook event) {
        Booking booking = lockBooking(event.bookingId());
        Payment prior = payments.findByIdempotencyKey(event.idempotencyKey());
        if (prior == null) { prior = payments.findByGatewayTransactionId(event.gatewayTransactionId()); }
        if (prior != null) {
            if (!samePayment(prior, event)) {
                throw new BookingException("Payment reference conflicts with an existing event");
            }
            return WebhookResult.processed(prior);
        }
        if (booking.getTotalAmount().compareTo(event.amount()) != 0 || !booking.getCurrency().equals(event.currency())) {
            throw new BookingException("Payment amount or currency does not match the booking");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        PaymentResolution resolution;
        if (event.outcome() == PaymentStatus.FAILED) {
            if (booking.getStatus() == BookingStatus.PENDING) {
                inventory.release(booking.getId());
                booking.setStatus(BookingStatus.CANCELLED);
                bookings.save(booking);
            }
            resolution = PaymentResolution.DECLINED;
        } else {
            List<BookingItem> expected = items.findByBookingIdOrderBySeatId(booking.getId());
            List<ShowSeat> allocation = inventory.findAllocationForUpdate(booking.getId());
            boolean showOpen = shows.findById(booking.getShowId())
                    .map(show -> show.getStatus() == ShowStatus.ACTIVE && show.getStartTime().isAfter(now))
                    .orElse(false);
            boolean payable = booking.getStatus() == BookingStatus.PENDING && booking.getExpiresAt().isAfter(now) &&
                    showOpen && allocationMatches(booking, expected, allocation, now);
            if (!payable) {
                // Real money can arrive late. Record the obligation without taking another user's seats.
                if (booking.getStatus() == BookingStatus.PENDING) {
                    inventory.release(booking.getId());
                    booking.setStatus(BookingStatus.EXPIRED);
                    bookings.save(booking);
                }
                resolution = PaymentResolution.REFUND_REQUIRED;
            } else {
                int confirmed = inventory.confirm(booking.getShowId(), booking.getId(), booking.getUserId(), now);
                if (confirmed != expected.size()) {
                    throw new BookingException("Seat confirmation was incomplete");
                }
                booking.setStatus(BookingStatus.CONFIRMED);
                bookings.save(booking);
                resolution = PaymentResolution.APPLIED;
            }
        }
        Payment payment = new Payment();
        payment.setBookingId(event.bookingId());
        payment.setGatewayTransactionId(event.gatewayTransactionId());
        payment.setIdempotencyKey(event.idempotencyKey());
        payment.setAmount(event.amount());
        payment.setCurrency(event.currency());
        payment.setStatus(event.outcome());
        payment.setResolution(resolution);
        // Flush uniqueness violations inside the transactional proxy so every preceding write rolls back.
        payments.saveAndFlush(payment);
        return WebhookResult.processed(payment);
    }

    @Transactional
    public boolean expireBooking(String bookingId) {
        Booking booking = lockBooking(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING || booking.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
            return false;
        }
        inventory.release(bookingId);
        booking.setStatus(BookingStatus.EXPIRED);
        bookings.save(booking);
        return true;
    }

    @Transactional
    public BookingResult cancelBooking(String bookingId, Long userId) {
        Booking booking = lockBooking(bookingId);
        if (!Objects.equals(booking.getUserId(), userId)) { throw new BookingException("Booking does not belong to this user"); }
        if (booking.getStatus() == BookingStatus.CONFIRMED) { throw new BookingException("Confirmed bookings require a refund workflow"); }
        if (booking.getStatus() == BookingStatus.PENDING) {
            inventory.release(bookingId);
            booking.setStatus(BookingStatus.CANCELLED);
            bookings.save(booking);
        }
        return BookingResult.from(booking, "Booking is no longer holding seats");
    }

    private Booking lockBooking(String id) {
        if (id == null || id.isBlank()) { throw new BookingException("Booking ID is required"); }
        return bookings.findByIdForUpdate(id).orElseThrow(() -> new BookingException("Booking not found"));
    }

    private boolean samePayment(Payment prior, PaymentWebhook event) {
        return prior.getBookingId().equals(event.bookingId()) &&
                prior.getGatewayTransactionId().equals(event.gatewayTransactionId()) &&
                prior.getStatus() == event.outcome() && prior.getAmount().compareTo(event.amount()) == 0 &&
                prior.getCurrency().equals(event.currency());
    }

    private boolean allocationMatches(Booking booking, List<BookingItem> expected, List<ShowSeat> allocation,
                                      LocalDateTime now) {
        if (expected.isEmpty() || allocation.size() != expected.size()) { return false; }
        var expectedIds = expected.stream().map(i -> new ShowSeatId(i.getShowId(), i.getSeatId())).collect(Collectors.toSet());
        if (expectedIds.size() != expected.size() || expected.stream().anyMatch(i -> !i.getShowId().equals(booking.getShowId())) ||
                expected.stream().map(BookingItem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .compareTo(booking.getTotalAmount()) != 0) { return false; }
        return allocation.stream().allMatch(s -> expectedIds.contains(s.getId()) &&
                s.getStatus() == InventoryStatus.HOLD && Objects.equals(s.getHeldByUserId(), booking.getUserId()) &&
                s.getHeldUntil() != null && s.getHeldUntil().isAfter(now));
    }
}
