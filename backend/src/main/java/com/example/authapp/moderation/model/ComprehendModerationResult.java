package com.example.authapp.moderation.model;

import java.util.List;
import java.util.stream.Collectors;

public record ComprehendModerationResult(
        String languageCode,
        TextComponentModeration titleComponent,
        TextComponentModeration bodyComponent
) {

    public static ComprehendModerationResult empty(String languageCode) {
        TextComponentModeration title = new TextComponentModeration("TITLE", 0.0, false, false, false, "");
        TextComponentModeration body = new TextComponentModeration("CONTENT", 0.0, false, false, false, "");
        return new ComprehendModerationResult(languageCode, title, body);
    }

    public List<TextComponentModeration> components() {
        return List.of(titleComponent, bodyComponent);
    }

    public double maxNegativeScore() {
        return components().stream()
                .mapToDouble(TextComponentModeration::negativeScore)
                .max()
                .orElse(0.0);
    }

    public boolean hasPiiDetected() {
        return components().stream().anyMatch(TextComponentModeration::piiDetected);
    }

    public boolean requiresReview(double reviewThreshold) {
        return components().stream()
                .anyMatch(component -> component.negativeScore() >= reviewThreshold || component.review());
    }

    public String combinedSummary() {
        return components().stream()
                .map(component -> component.component() + ":" + component.summary())
                .collect(Collectors.joining("; "));
    }
}
