package com.taskmanagement.common;

public final class Text {
    private Text() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
