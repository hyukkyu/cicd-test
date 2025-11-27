package com.example.authapp.moderation.service;

import com.example.authapp.config.AppProperties;
import com.example.authapp.moderation.*;
import com.example.authapp.moderation.client.ComprehendClient;
import com.example.authapp.moderation.client.RekognitionClient;
import com.example.authapp.moderation.client.S3Uploader;
import com.example.authapp.moderation.client.SnsPublisher;
import com.example.authapp.moderation.dto.ModeratedContentResponse;
import com.example.authapp.moderation.dto.ModerationRequest;
import com.example.authapp.moderation.dto.TextModerationRequest;
import com.example.authapp.moderation.dto.TextModerationResponse;
import com.example.authapp.moderation.model.ComprehendModerationResult;
import com.example.authapp.moderation.model.MediaModerationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);

    private final ModeratedContentRepository moderatedContentRepository;
    private final AdminAlertRepository adminAlertRepository;
    private final S3Uploader s3Uploader;
    private final SnsPublisher snsPublisher;
    private final ComprehendClient comprehendClient;
    private final RekognitionClient rekognitionClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ContentModerationService(ModeratedContentRepository moderatedContentRepository,
                                    AdminAlertRepository adminAlertRepository,
                                    S3Uploader s3Uploader,
                                    SnsPublisher snsPublisher,
                                    ComprehendClient comprehendClient,
                                    RekognitionClient rekognitionClient,
                                    AppProperties appProperties,
                                    ObjectMapper objectMapper) {
        this.moderatedContentRepository = moderatedContentRepository;
        this.adminAlertRepository = adminAlertRepository;
        this.s3Uploader = s3Uploader;
        this.snsPublisher = snsPublisher;
        this.comprehendClient = comprehendClient;
        this.rekognitionClient = rekognitionClient;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ModeratedContentResponse submitContent(ModerationRequest request, MultipartFile mediaFile) {
        ModeratedContent content = new ModeratedContent();
        content.setTitle(request.title());
        content.setBody(request.body());
        content.setAuthorId(request.authorId());
        content.setBoard(request.board());

        MediaModerationResult mediaResult = MediaModerationResult.empty();
        if (mediaFile != null && !mediaFile.isEmpty()) {
            mediaResult = handleMediaUpload(content, mediaFile);
        }
        content.setMediaScore(mediaResult.highestConfidence());

        ComprehendModerationResult textResult = comprehendClient.analyze(request.title(), request.body(), null);
        content.setTextScore(textResult.maxNegativeScore());
        applyTextDecision(content, textResult);

        ModeratedContent saved = moderatedContentRepository.save(content);
        return ModeratedContentResponse.from(saved, textResult, mediaResult);
    }

    @Transactional(readOnly = true)
    public ModeratedContentResponse getContent(String referenceId) {
        ModeratedContent content = moderatedContentRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));
        return ModeratedContentResponse.from(
                content,
                ComprehendModerationResult.empty("ko"),
                MediaModerationResult.empty()
        );
    }

    public TextModerationResponse analyzeText(TextModerationRequest request) {
        ComprehendModerationResult analysis = comprehendClient.analyze(
                request.title(),
                request.body(),
                request.language()
        );
        boolean blocked = shouldBlockByText(analysis);
        String message = blocked ? "유해 게시글로 차단되었습니다." : "검수 통과";
        return new TextModerationResponse(blocked, message, analysis);
    }

    private MediaModerationResult handleMediaUpload(ModeratedContent content, MultipartFile mediaFile) {
        try {
            S3Uploader.UploadedObject uploaded = s3Uploader.upload(mediaFile, "moderation/" + content.getReferenceId());
            content.setMediaBucket(uploaded.bucket());
            content.setMediaKey(uploaded.key());
            content.setMediaUrl(uploaded.url());
            content.setStatus(ModerationStatus.QUEUED);
            snsPublisher.publishModerationRequest(content);

            if (uploaded.isImage()) {
                MediaModerationResult result = rekognitionClient.analyzeImage(uploaded.bucket(), uploaded.key());
                content.setMediaScore(result.highestConfidence());
                if (result.blocked()) {
                    log.warn("Media flagged during synchronous scan. referenceId={}", content.getReferenceId());
                    blockContent(content, "IMAGE_FLAGGED");
                    createAdminAlert(content, AdminAlertType.MEDIA, "이미지 유해물 감지", result);
                }
                return result;
            }
            return MediaModerationResult.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("파일 업로드 중 오류가 발생했습니다.", ex);
        }
    }

    private void applyTextDecision(ModeratedContent content, ComprehendModerationResult result) {
        if (result == null) {
            return;
        }
        double reviewThreshold = appProperties.getModeration().getReviewThreshold();

        if (shouldBlockByText(result)) {
            blockContent(content, "TEXT_FLAGGED");
            createAdminAlert(content, AdminAlertType.TEXT, "본문에서 유해성이 감지되었습니다.", result.components());
        } else if (result.requiresReview(reviewThreshold)) {
            content.markReview("TEXT_REVIEW_REQUIRED");
            createAdminAlert(content, AdminAlertType.TEXT, "관리자 검토 필요", result.components());
        } else if (!content.isBlocked() && content.getStatus() != ModerationStatus.QUEUED) {
            content.markApproved();
        }
    }

    private boolean shouldBlockByText(ComprehendModerationResult result) {
        double threshold = appProperties.getModeration().getTextBlockThreshold();
        return result.hasPiiDetected() || result.maxNegativeScore() >= threshold;
    }

    private void blockContent(ModeratedContent content, String reason) {
        content.markBlocked(reason, content.getTextScore(), content.getMediaScore());
    }

    private void createAdminAlert(ModeratedContent content, AdminAlertType type, String reason, Object payload) {
        AdminAlert alert = new AdminAlert();
        alert.setType(type);
        alert.setReason(reason);
        alert.setPayload(serializePayload(payload));
        content.addAlert(alert);
        adminAlertRepository.save(alert);
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String str && StringUtils.hasText(str)) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload for admin alert: {}", e.getMessage());
            return payload.toString();
        }
    }
}
