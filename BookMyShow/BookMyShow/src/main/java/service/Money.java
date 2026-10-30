package service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
    private Money() {}
    static BigDecimal validate(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("Amount must be a non-negative decimal within the supported range");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must have at most two decimal places");
        }
    }
}
