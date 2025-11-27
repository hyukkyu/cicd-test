package com.example.authapp.moderation.dto;

import com.example.authapp.moderation.model.ComprehendModerationResult;

public record TextModerationResponse(
        boolean blocked,
        String message,
        ComprehendModerationResult analysis
) {
}
