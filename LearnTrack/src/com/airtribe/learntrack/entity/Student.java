package com.airtribe.learntrack.entity;

public class Student extends Person {

    private String  batch;
    private boolean active;

    public Student(String id, String firstName, String lastName,
                   String email, String phone, String batch) {
        super(id, firstName, lastName, email, phone);
        this.batch  = batch;
        this.active = true;
    }

    public String getBatch()              { return batch; }
    public void setBatch(String batch)    { this.batch = batch; }

    public boolean isActive()             { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return String.format(
                "Student[id=%s | name=%s | email=%s | phone=%s | batch=%s | active=%s | joined=%s]",
                getId(), getFullName(), getEmail(), getPhone(), batch, active, getCreatedAtFormatted());
    }
}