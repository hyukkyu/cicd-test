package com.example.authapp.moderation;

/**
 * Represents the state of a piece of user generated content inside the
 * moderation pipeline.
 */
public enum ModerationStatus {
    PENDING,
    QUEUED,
    REVIEW,
    APPROVED,
    BLOCKED
}
