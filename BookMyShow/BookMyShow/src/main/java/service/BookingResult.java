package service;

import entity.Booking;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingResult(boolean success, String message, String bookingId,
                            LocalDateTime heldUntil, BigDecimal totalAmount, String currency) {
    public static BookingResult from(Booking booking, String message) {
        return new BookingResult(true, message, booking.getId(), booking.getExpiresAt(),
                booking.getTotalAmount(), booking.getCurrency());
    }
    public static BookingResult failed(String message) {
        return new BookingResult(false, message, null, null, null, null);
    }
}
