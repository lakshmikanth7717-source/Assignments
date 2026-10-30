package com.airtribe.learntrack.util;

import com.airtribe.learntrack.exception.InvalidInputException;

public final class InputValidator {

    private static final String EMAIL_REGEX = "^[\\w._%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$";
    private static final String PHONE_REGEX = "^[0-9]{10}$";
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private InputValidator() {
        throw new UnsupportedOperationException("Utility class — do not instantiate.");
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    public static void validateEmail(String email) {
        requireNonBlank(email, "Email");
        if (!email.trim().matches(EMAIL_REGEX)) {
            throw new InvalidInputException("Invalid email format: '" + email.trim() + "'.");
        }
    }

    public static void validatePhone(String phone) {
        requireNonBlank(phone, "Phone");
        if (!phone.trim().matches(PHONE_REGEX)) {
            throw new InvalidInputException("Phone must be exactly 10 digits. Got: '" + phone.trim() + "'.");
        }
    }

    public static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new InvalidInputException(fieldName + " must be a positive number. Got: " + value);
        }
        return value;
    }

    public static void validateMaxLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidInputException(fieldName + " cannot exceed " + maxLength + " characters.");
        }
    }
}