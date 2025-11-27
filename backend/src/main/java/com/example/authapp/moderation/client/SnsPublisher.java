package com.example.authapp.moderation.client;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.example.authapp.config.AppProperties;
import com.example.authapp.moderation.ModeratedContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Component
public class SnsPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsPublisher.class);

    private final AmazonSNS amazonSNS;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public SnsPublisher(AmazonSNS amazonSNS,
                        ObjectMapper objectMapper,
                        AppProperties appProperties) {
        this.amazonSNS = amazonSNS;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public void publishModerationRequest(ModeratedContent content) {
        String topicArn = resolveTopicArn();
        if (!StringUtils.hasText(topicArn)) {
            log.warn("Skip publishing moderation request because SNS topic ARN is missing");
            return;
        }

        ModerationPipelineMessage payload = new ModerationPipelineMessage(
                content.getReferenceId(),
                content.getMediaBucket(),
                content.getMediaKey(),
                content.getMediaUrl(),
                content.getAuthorId(),
                content.getTitle(),
                content.getBody(),
                Instant.now().toString()
        );

        try {
            String message = objectMapper.writeValueAsString(payload);
            PublishRequest request = new PublishRequest()
                    .withTopicArn(topicArn)
                    .withSubject("ContentModerationRequest")
                    .withMessage(message);
            amazonSNS.publish(request);
            log.info("Published moderation request to SNS topic={} referenceId={}", topicArn, content.getReferenceId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize moderation message", e);
        }
    }

    private String resolveTopicArn() {
        if (StringUtils.hasText(appProperties.getModeration().getPipelineTopicArn())) {
            return appProperties.getModeration().getPipelineTopicArn();
        }
        return appProperties.getAws().getSnsTopicArn();
    }

    public record ModerationPipelineMessage(
            String contentId,
            String mediaBucket,
            String mediaKey,
            String mediaUrl,
            String authorId,
            String title,
            String body,
            String requestedAt
    ) {
    }
}
