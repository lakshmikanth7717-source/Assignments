package service;

public record BookingResult(boolean success, String message) {
    public static BookingResult success(String message) {
        return new BookingResult(true, message);
    }

    public static BookingResult failed(String message) {
        return new BookingResult(false, message);
    }
}

