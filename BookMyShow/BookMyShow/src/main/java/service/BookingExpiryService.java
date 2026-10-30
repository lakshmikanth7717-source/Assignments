package service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import repo.BookingRepository;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class BookingExpiryService {
    private static final Logger log = LoggerFactory.getLogger(BookingExpiryService.class);
    private final BookingRepository bookings;
    private final BookingTransactionService database;
    private final Clock clock;

    public BookingExpiryService(BookingRepository bookings, BookingTransactionService database, Clock clock) {
        this.bookings = bookings;
        this.database = database;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-interval-ms:30000}")
    public void expireBookings() {
        for (String id : bookings.findExpiredBookingIds(LocalDateTime.now(clock), PageRequest.of(0, 100))) {
            try {
                database.expireBooking(id);
            } catch (RuntimeException e) {
                // A failed booking remains pending and will be retried on the next sweep.
                log.error("Could not expire booking {}", id, e);
            }
        }
    }
}
