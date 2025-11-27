package com.example.authapp.moderation.client;

import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.model.AmazonComprehendException;
import com.amazonaws.services.comprehend.model.DetectPiiEntitiesRequest;
import com.amazonaws.services.comprehend.model.DetectPiiEntitiesResult;
import com.amazonaws.services.comprehend.model.DetectSentimentRequest;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;
import com.example.authapp.moderation.model.ComprehendModerationResult;
import com.example.authapp.moderation.model.TextComponentModeration;
import com.example.authapp.post.TextModerationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Component
public class ComprehendClient {

    private static final Logger log = LoggerFactory.getLogger(ComprehendClient.class);
    private static final int MAX_TEXT_LENGTH = 4500;
    private static final Set<String> COMPREHEND_PII_SUPPORTED_LANGUAGES = Set.of("en", "es");

    private final AmazonComprehend amazonComprehend;
    private final AmazonTranslate amazonTranslate;

    public ComprehendClient(AmazonComprehend amazonComprehend, AmazonTranslate amazonTranslate) {
        this.amazonComprehend = amazonComprehend;
        this.amazonTranslate = amazonTranslate;
    }

    public ComprehendModerationResult analyze(String title, String body, String languageOverride) {
        String languageCode = resolveLanguage(languageOverride);
        TextModerationOutcome titleOutcome = moderateText(title, languageCode);
        TextModerationOutcome bodyOutcome = moderateText(body, languageCode);
        return new ComprehendModerationResult(
                languageCode,
                TextComponentModeration.from("TITLE", titleOutcome),
                TextComponentModeration.from("CONTENT", bodyOutcome)
        );
    }

    private TextModerationOutcome moderateText(String text, String languageCode) {
        if (!StringUtils.hasText(text)) {
            return new TextModerationOutcome(false, false, false, 0.0, "", null, null);
        }

        String trimmedText = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;

        try {
            DetectSentimentRequest sentimentRequest = new DetectSentimentRequest()
                    .withText(trimmedText)
                    .withLanguageCode(languageCode);
            DetectSentimentResult sentimentResult = amazonComprehend.detectSentiment(sentimentRequest);
            SentimentScore sentimentScore = sentimentResult.getSentimentScore();

            double negativeScore = sentimentScore != null && sentimentScore.getNegative() != null
                    ? sentimentScore.getNegative()
                    : 0.0d;

            boolean piiDetected = false;
            int piiCount = 0;
            boolean piiScanAttempted = false;
            DetectPiiEntitiesResult piiResult = null;

            String textToModerate = trimmedText;
            String languageForPii = languageCode;

            if ("ko".equals(languageCode)) {
                try {
                    TranslateTextRequest request = new TranslateTextRequest()
                            .withText(trimmedText)
                            .withSourceLanguageCode("ko")
                            .withTargetLanguageCode("en");
                    TranslateTextResult result = amazonTranslate.translateText(request);
                    textToModerate = result.getTranslatedText();
                    languageForPii = "en";
                } catch (Exception e) {
                    log.error("Failed to translate text from Korean to English for moderation.", e);
                }
            }

            if (supportsPiiDetection(languageForPii)) {
                piiScanAttempted = true;
                DetectPiiEntitiesRequest piiRequest = new DetectPiiEntitiesRequest()
                        .withText(textToModerate)
                        .withLanguageCode(languageForPii);
                piiResult = amazonComprehend.detectPiiEntities(piiRequest);
                piiDetected = piiResult.getEntities() != null && !piiResult.getEntities().isEmpty();
                piiCount = piiDetected ? piiResult.getEntities().size() : 0;
            } else {
                log.debug("Skipping PII detection for languageCode='{}'. Supported languages={}.", languageCode, COMPREHEND_PII_SUPPORTED_LANGUAGES);
            }

            boolean isBlocked = piiDetected;
            boolean isReview = negativeScore >= 0.01d;
            boolean notifyAdmin = isBlocked || isReview;
            String summary = buildTextSummary(negativeScore, sentimentResult.getSentiment(), piiCount, piiScanAttempted);

            return new TextModerationOutcome(notifyAdmin, piiDetected, piiScanAttempted, negativeScore, summary, sentimentResult, piiResult);

        } catch (AmazonComprehendException e) {
            log.error("Failed to run text moderation. Falling back to visible status.", e);
            return new TextModerationOutcome(false, false, false, 0.0, "Error", null, null);
        }
    }

    private String buildTextSummary(double negativeScore, String dominantSentiment, int piiCount, boolean piiScanAttempted) {
        String summary = String.format("sentiment=%s, negative=%.3f, piiCount=%d", dominantSentiment, negativeScore, piiCount);
        if (!piiScanAttempted) {
            summary += " (pii-scan-skipped)";
        }
        return summary;
    }

    private boolean supportsPiiDetection(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        return COMPREHEND_PII_SUPPORTED_LANGUAGES.contains(languageCode.toLowerCase(Locale.ROOT));
    }

    private String resolveLanguage(String override) {
        if (StringUtils.hasText(override)) {
            return override.toLowerCase(Locale.ROOT);
        }
        return "ko";
    }
}
