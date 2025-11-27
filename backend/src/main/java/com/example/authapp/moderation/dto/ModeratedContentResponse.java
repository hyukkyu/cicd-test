package com.example.authapp.moderation.dto;

import com.example.authapp.moderation.ModeratedContent;
import com.example.authapp.moderation.ModerationStatus;
import com.example.authapp.moderation.model.ComprehendModerationResult;
import com.example.authapp.moderation.model.MediaModerationResult;

import java.util.List;
import java.util.stream.Collectors;

public record ModeratedContentResponse(
        String referenceId,
        ModerationStatus status,
        boolean blocked,
        String blockReason,
        double textScore,
        double mediaScore,
        String mediaUrl,
        ComprehendModerationResult textAnalysis,
        MediaModerationResult mediaAnalysis,
        List<AdminAlertDto> alerts
) {
    public static ModeratedContentResponse from(ModeratedContent content,
                                                ComprehendModerationResult textAnalysis,
                                                MediaModerationResult mediaAnalysis) {
        List<AdminAlertDto> alertDtos = content.getAlerts()
                .stream()
                .map(AdminAlertDto::from)
               .collect(Collectors.toList());

        return new ModeratedContentResponse(
                content.getReferenceId(),
                content.getStatus(),
                content.isBlocked(),
                content.getBlockReason(),
                content.getTextScore(),
                content.getMediaScore(),
                content.getMediaUrl(),
                textAnalysis,
                mediaAnalysis,
                alertDtos
        );
    }
}
