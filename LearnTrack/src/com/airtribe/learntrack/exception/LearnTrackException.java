package com.airtribe.learntrack.exception;

public class LearnTrackException extends RuntimeException {

    private final String errorCode;

    public LearnTrackException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}