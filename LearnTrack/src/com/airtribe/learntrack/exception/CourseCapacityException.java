package com.airtribe.learntrack.exception;

public class CourseCapacityException extends LearnTrackException {

    private static final String ERROR_CODE = "COURSE_CAPACITY_FULL";

    public CourseCapacityException(String courseId) {
        super("Course '" + courseId + "' has reached maximum capacity.", ERROR_CODE);
    }
}