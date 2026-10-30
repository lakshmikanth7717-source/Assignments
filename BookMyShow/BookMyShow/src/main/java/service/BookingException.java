package service;

/** A deliberate, safe-to-display rejection of a booking or payment operation. */
public class BookingException extends RuntimeException {
    public BookingException(String message) { super(message); }
}
