package com.airtribe.learntrack.util;

public final class IdGenerator {

    private static int studentIdCounter    = 1000;
    private static int courseIdCounter     = 2000;
    private static int enrollmentIdCounter = 3000;

    private IdGenerator() {
        throw new UnsupportedOperationException("Utility class — do not instantiate.");
    }

    public static String getNextStudentId() {
        return "STU-" + (++studentIdCounter);
    }

    public static String getNextCourseId() {
        return "CRS-" + (++courseIdCounter);
    }

    public static String getNextEnrollmentId() {
        return "ENR-" + (++enrollmentIdCounter);
    }
}