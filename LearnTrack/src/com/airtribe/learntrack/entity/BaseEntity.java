package com.airtribe.learntrack.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class BaseEntity {

    protected static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String        id;
    private final LocalDateTime createdAt;

    protected BaseEntity(String id) {
        this.id        = id;
        this.createdAt = LocalDateTime.now();
    }

    public String getId()                 { return id; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public String getCreatedAtFormatted() { return createdAt.format(FORMATTER); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BaseEntity)) return false;
        return id.equals(((BaseEntity) obj).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public abstract String toString();
}