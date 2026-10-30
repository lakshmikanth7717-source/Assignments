package service;

import entity.Payment;
import entity.PaymentResolution;

/** success means the event was processed, including declines and payments needing a refund. */
public record WebhookResult(boolean success, String message, boolean paymentApplied, boolean refundRequired) {
    public static WebhookResult processed(Payment payment) {
        PaymentResolution resolution = payment.getResolution();
        return switch (resolution) {
            case APPLIED -> new WebhookResult(true, "Payment processed and seats confirmed", true, false);
            case DECLINED -> new WebhookResult(true, "Payment failure recorded", false, false);
            case REFUND_REQUIRED -> new WebhookResult(true, "Payment recorded; refund required", false, true);
        };
    }
    public static WebhookResult failed(String message) {
        return new WebhookResult(false, message, false, false);
    }
}
