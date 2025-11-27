package com.example.authapp.post;

import com.amazonaws.services.comprehend.model.DetectPiiEntitiesResult;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TextModerationOutcome {
    private final boolean shouldNotify;
    private final boolean piiDetected;
    private final boolean piiScanAttempted;
    private final double negativeScore;
    private final String summary;
    private final DetectSentimentResult sentimentResult;
    private final DetectPiiEntitiesResult piiResult;

    public TextModerationOutcome(boolean shouldNotify, boolean piiDetected, boolean piiScanAttempted, double negativeScore, String summary, DetectSentimentResult sentimentResult, DetectPiiEntitiesResult piiResult) {
        this.shouldNotify = shouldNotify;
        this.piiDetected = piiDetected;
        this.piiScanAttempted = piiScanAttempted;
        this.negativeScore = negativeScore;
        this.summary = summary;
        this.sentimentResult = sentimentResult;
        this.piiResult = piiResult;
    }

    public boolean shouldNotify() {
        return shouldNotify;
    }

    public boolean isPiiDetected() {
        return piiDetected;
    }

    public boolean isPiiScanAttempted() {
        return piiScanAttempted;
    }

    public double getNegativeScore() {
        return negativeScore;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isBlocked() {
        return piiDetected;
    }

    public boolean isReview() {
        return negativeScore >= 0.01d;
    }

    public String serializeModerationResult(ObjectMapper objectMapper) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("summary", summary);
            payload.put("piiDetected", piiDetected);
            payload.put("piiScanAttempted", piiScanAttempted);
            payload.put("negativeScore", negativeScore);
            payload.put("sentiment", sentimentResult != null ? sentimentResult.getSentiment() : null);
            payload.put("sentimentScore", sentimentResult != null ? sentimentResult.getSentimentScore() : null);
            payload.put("piiEntities", piiResult != null ? piiResult.getEntities() : null);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"summary\":\"" + summary + "\"}";
        }
    }
}
