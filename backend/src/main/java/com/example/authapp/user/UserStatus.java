package com.example.authapp.user;

/**
 * Represents the logical state of a user account so administrators can
 * distinguish active, blocked, and deleted members.
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    DELETED
}
