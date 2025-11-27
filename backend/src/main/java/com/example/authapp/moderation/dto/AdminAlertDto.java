package com.example.authapp.moderation.dto;

import com.example.authapp.moderation.AdminAlert;
import com.example.authapp.moderation.AdminAlertType;

import java.time.Instant;

public record AdminAlertDto(
        Long id,
        AdminAlertType type,
        String reason,
        String payload,
        Instant createdAt,
        boolean acknowledged
) {
    public static AdminAlertDto from(AdminAlert alert) {
        return new AdminAlertDto(
                alert.getId(),
                alert.getType(),
                alert.getReason(),
                alert.getPayload(),
                alert.getCreatedAt(),
                alert.isAcknowledged()
        );
    }
}
