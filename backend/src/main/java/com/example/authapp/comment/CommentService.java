package com.example.authapp.comment;

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
import com.example.authapp.admin.AdminReviewItem;
import com.example.authapp.admin.AdminReviewItemRepository;
import com.example.authapp.admin.NotificationService;
import com.example.authapp.post.Post;
import com.example.authapp.post.TextModerationOutcome;
import com.example.authapp.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private static final Set<String> COMPREHEND_PII_SUPPORTED_LANGUAGES = Set.of("en", "es");

    private final CommentRepository commentRepository;
    private final AmazonTranslate amazonTranslate;
    private final AmazonComprehend amazonComprehend;
    private final AdminReviewItemRepository adminReviewItemRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public CommentService(CommentRepository commentRepository,
                          AmazonTranslate amazonTranslate,
                          AmazonComprehend amazonComprehend,
                          AdminReviewItemRepository adminReviewItemRepository,
                          NotificationService notificationService,
                          ObjectMapper objectMapper) {
        this.commentRepository = commentRepository;
        this.amazonTranslate = amazonTranslate;
        this.amazonComprehend = amazonComprehend;
        this.adminReviewItemRepository = adminReviewItemRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public Comment create(Post post, String content, User author, Comment parent) {
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setCreateDate(LocalDateTime.now());
        comment.setPost(post);
        comment.setAuthor(author);
        if (parent != null) {
            comment.setParent(parent);
        }

        this.commentRepository.save(comment);

        moderateCommentContent(comment, content);

        return comment;
    }

    private void moderateCommentContent(Comment comment, String content) {
        TextModerationOutcome outcome = moderateText(content);

        if (outcome.shouldNotify()) {
            createAdminReviewItemForComment(comment, outcome);
        }
    }

    private TextModerationOutcome moderateText(String text) {
        if (text == null || text.isBlank()) {
            return new TextModerationOutcome(false, false, false, 0.0, "", null, null);
        }

        String trimmedText = text.length() > 4500 ? text.substring(0, 4500) : text;
        String languageCode = "ko"; // Assuming comments are in Korean

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
            log.error("Failed to run text moderation for text. Falling back to visible status.", e);
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

    private void createAdminReviewItemForComment(Comment comment, TextModerationOutcome outcome) {
        AdminReviewItem adminReviewItem = new AdminReviewItem();
        adminReviewItem.setPost(comment.getPost());
        adminReviewItem.setComment(comment);
        adminReviewItem.setContentType("COMMENT");
        String originalText = comment.getContent() != null ? comment.getContent() : "";
        adminReviewItem.setModeratedText("[COMMENT] " + originalText);
        adminReviewItem.setModerationResult(outcome.serializeModerationResult(objectMapper));
        adminReviewItem.setInappropriateDetected(true);
        adminReviewItem.setReviewStatus(AdminReviewItem.ReviewStatus.PENDING);
        adminReviewItemRepository.save(adminReviewItem);
        notificationService.notifyAdmin(adminReviewItem);
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private boolean supportsPiiDetection(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        return COMPREHEND_PII_SUPPORTED_LANGUAGES.contains(languageCode.toLowerCase(java.util.Locale.ROOT));
    }

    public Comment getComment(Long id) {
        Optional<Comment> comment = this.commentRepository.findById(id);
        if (comment.isPresent()) {
            return comment.get();
        } else {
            throw new RuntimeException("Comment not found");
        }
    }

    public void update(Comment comment, String content) {
        comment.setContent(content);
        this.commentRepository.save(comment);
    }

    public void delete(Comment comment) {
        this.commentRepository.delete(comment);
    }
}
