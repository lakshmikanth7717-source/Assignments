package com.airtribe.learntrack.entity;

public class Enrollment extends BaseEntity {

    public enum Status {
        ACTIVE, CANCELLED, COMPLETED
    }

    private final String studentId;
    private final String courseId;
    private Status status;

    public Enrollment(String id, String studentId, String courseId) {
        super(id);
        this.studentId = studentId;
        this.courseId = courseId;
        this.status = Status.ACTIVE;
    }

    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public boolean isActive() { return status == Status.ACTIVE; }

    @Override
    public String toString() {
        return String.format("Enrollment[id=%s | student=%s | course=%s | status=%s | date=%s]",
                getId(), studentId, courseId, status, getCreatedAtFormatted());
    }
}