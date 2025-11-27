package com.example.authapp.notification;

import java.time.LocalDateTime;

public record UserNotificationDto(
        Long id,
        UserNotificationType type,
        String message,
        String link,
        boolean read,
        boolean blocked,
        String blockedReason,
        LocalDateTime createdAt
) {
}
