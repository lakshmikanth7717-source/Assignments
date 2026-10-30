package com.airtribe.learntrack.exception;

public class DuplicateEnrollmentException extends LearnTrackException {

    private static final String ERROR_CODE = "DUPLICATE_ENROLLMENT";

    public DuplicateEnrollmentException(String studentId, String courseId) {
        super("Student '" + studentId + "' is already actively enrolled in course '" + courseId + "'.", ERROR_CODE);
    }
}