package service;

public record WebhookResult(boolean success, String message) {
    public static WebhookResult success(String message) {
        return new WebhookResult(true, message);
    }

    public static WebhookResult failed(String message) {
        return new WebhookResult(false, message);
    }
}

