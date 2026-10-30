package com.airtribe.learntrack.exception;

public class InvalidInputException extends LearnTrackException {

    private static final String ERROR_CODE = "INVALID_INPUT";

    public InvalidInputException(String message) {
        super(message, ERROR_CODE);
    }
}