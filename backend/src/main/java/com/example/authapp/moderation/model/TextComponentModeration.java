package com.example.authapp.moderation.model;

import com.example.authapp.post.TextModerationOutcome;

public record TextComponentModeration(
        String component,
        double negativeScore,
        boolean piiDetected,
        boolean review,
        boolean blocked,
        String summary
) {

    public static TextComponentModeration from(String component, TextModerationOutcome outcome) {
        if (outcome == null) {
            return new TextComponentModeration(component, 0.0, false, false, false, "");
        }
        return new TextComponentModeration(
                component,
                outcome.getNegativeScore(),
                outcome.isPiiDetected(),
                outcome.isReview(),
                outcome.isBlocked(),
                outcome.getSummary()
        );
    }
}
