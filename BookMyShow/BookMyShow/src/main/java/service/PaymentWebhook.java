package service;

import entity.PaymentStatus;
import java.math.BigDecimal;

/** Terminal payment event. Authentication is performed on the original JSON before deserialization. */
public record PaymentWebhook(String idempotencyKey, String bookingId, String gatewayTransactionId,
                             BigDecimal amount, String currency, PaymentStatus outcome) {
    public PaymentWebhook {
        requireReference(idempotencyKey, 128, "idempotency key");
        requireReference(bookingId, 64, "booking ID");
        requireReference(gatewayTransactionId, 128, "gateway transaction ID");
        amount = Money.validate(amount);
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase code");
        }
        if (outcome == null) { throw new IllegalArgumentException("Payment outcome is required"); }
    }

    private static void requireReference(String value, int length, String name) {
        if (value == null || value.isBlank() || value.length() > length || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }
}
