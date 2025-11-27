package com.example.authapp.admin;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        NotificationType type,
        String message,
        Long targetId,
        boolean read,
        LocalDateTime createdAt,
        String link,
        String targetLabel,
        String authorLabel,
        String boardLabel,
        String summary,
        String mainBoardName,
        String subBoardName,
        String reporterLabel,
        String detectionLabel
) {
}
