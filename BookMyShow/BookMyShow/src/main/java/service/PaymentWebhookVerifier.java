package service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class PaymentWebhookVerifier {
    private final ObjectMapper mapper;
    private final String secret;

    public PaymentWebhookVerifier(ObjectMapper mapper, @Value("${booking.webhook-secret:}") String secret) {
        this.mapper = mapper;
        this.secret = secret;
    }

    public PaymentWebhook verify(String rawJson, String signature) {
        if (secret == null || secret.isBlank()) {
            throw new BookingException("Payment verification is not configured");
        }
        if (rawJson == null || rawJson.length() > 65536 || signature == null || !signature.matches("[a-fA-F0-9]{64}")) {
            throw new BookingException("Invalid payment signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawJson.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) {
                throw new BookingException("Invalid payment signature");
            }
            return mapper.readValue(rawJson, PaymentWebhook.class);
        } catch (JsonProcessingException e) {
            throw new BookingException("Invalid payment payload");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Payment verification unavailable", e);
        }
    }
}
