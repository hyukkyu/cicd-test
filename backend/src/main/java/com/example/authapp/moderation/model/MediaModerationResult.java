package com.example.authapp.moderation.model;

import java.util.Collections;
import java.util.List;

public record MediaModerationResult(
        double highestConfidence,
        boolean blocked,
        List<String> labels,
        String decisionReason
) {

    public static MediaModerationResult empty() {
        return new MediaModerationResult(0, false, Collections.emptyList(), "NO_MEDIA");
    }
}
