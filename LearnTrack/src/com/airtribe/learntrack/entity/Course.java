package com.airtribe.learntrack.entity;

public class Course extends BaseEntity {

    private String  courseName;
    private String  description;
    private int     durationInWeeks;
    private boolean active;

    public Course(String id, String courseName, String description, int durationInWeeks) {
        super(id);
        this.courseName      = courseName;
        this.description     = description;
        this.durationInWeeks = durationInWeeks;
        this.active          = true;
    }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDurationInWeeks() { return durationInWeeks; }
    public void setDurationInWeeks(int durationInWeeks) { this.durationInWeeks = durationInWeeks; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return String.format("Course[id=%s | name=%s | duration=%d weeks | active=%s | desc=%s]",
                getId(), courseName, durationInWeeks, active, description);
    }
}