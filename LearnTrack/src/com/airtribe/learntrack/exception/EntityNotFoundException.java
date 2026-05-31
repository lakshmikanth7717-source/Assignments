package com.airtribe.learntrack.exception;

public class EntityNotFoundException extends LearnTrackException {

    private static final String ERROR_CODE = "ENTITY_NOT_FOUND";

    public EntityNotFoundException(String entityType, String id) {
        super(entityType + " with ID '" + id + "' not found.", ERROR_CODE);
    }
}