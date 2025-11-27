package com.example.authapp.video;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.ContentModerationDetection;
import com.amazonaws.services.rekognition.model.GetContentModerationRequest;
import com.amazonaws.services.rekognition.model.GetContentModerationResult;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostRepository;
import com.example.authapp.s3.S3Service;
import com.example.authapp.video.dto.RekognitionMessage;
import com.example.authapp.video.dto.SnsMessage;
import com.example.authapp.video.dto.VideoModerationLambdaResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.messaging.listener.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.video", name = "sqs-enabled", havingValue = "true")
public class SqsListenerService {

    private static final Logger log = LoggerFactory.getLogger(SqsListenerService.class);

    private final ObjectMapper objectMapper;
    private final VideoModerationRepository videoModerationRepository;
    private final AmazonRekognition amazonRekognition;
    private final VideoModerationProcessingService videoModerationProcessingService;
    private final PostRepository postRepository;
    private final S3Service s3Service;

    public SqsListenerService(ObjectMapper objectMapper,
                              VideoModerationRepository videoModerationRepository,
                              AmazonRekognition amazonRekognition,
                              VideoModerationProcessingService videoModerationProcessingService,
                              PostRepository postRepository,
                              S3Service s3Service) {
        this.objectMapper = objectMapper;
        this.videoModerationRepository = videoModerationRepository;
        this.amazonRekognition = amazonRekognition;
        this.videoModerationProcessingService = videoModerationProcessingService;
        this.postRepository = postRepository;
        this.s3Service = s3Service;
    }

    @SqsListener("${cloud.aws.sqs.queue-name}")
    @Transactional
    public void receiveMessage(String messageJson) throws JsonProcessingException {
        log.info("Received SQS message for video moderation: {}", messageJson);
        JsonNode rootNode = objectMapper.readTree(messageJson);
        if (rootNode.has("Type")) {
            handleLegacyNotification(rootNode);
        } else {
            VideoModerationLambdaResult result = objectMapper.treeToValue(rootNode, VideoModerationLambdaResult.class);
            handleLambdaResult(result);
        }
    }

    private void handleLegacyNotification(JsonNode rootNode) throws JsonProcessingException {
        SnsMessage snsMessage = objectMapper.treeToValue(rootNode, SnsMessage.class);
        RekognitionMessage rekognitionMessage = objectMapper.readValue(snsMessage.getMessage(), RekognitionMessage.class);

        String jobId = rekognitionMessage.getJobId();
        String status = rekognitionMessage.getStatus();
        log.info("Parsed Rekognition callback (legacy). jobId={}, status={}", jobId, status);

        String objectKey = extractObjectKey(rekognitionMessage);
        VideoModeration videoModeration = ensureVideoModeration(jobId, status, objectKey);
        if (videoModeration == null) {
            return;
        }

        List<ModerationLabel> labels = Collections.emptyList();
        String resultJson = "[]";

        if ("SUCCEEDED".equals(status)) {
            log.info("Fetching moderation results for jobId={} (legacy)", jobId);
            GetContentModerationResult moderationResult = getModerationResult(jobId);
            labels = moderationResult.getModerationLabels().stream()
                    .map(ContentModerationDetection::getModerationLabel)
                    .collect(Collectors.toList());
            resultJson = objectMapper.writeValueAsString(labels);
            log.info("Fetched moderation labels for jobId={}, labelCount={}", jobId, labels.size());
        } else {
            log.warn("Video moderation job did not succeed. jobId={}, status={}", jobId, status);
        }

        String resolvedVideoUrl = resolveVideoUrl(videoModeration, objectKey);
        videoModerationProcessingService.handleResult(videoModeration, status, labels, resultJson, resolvedVideoUrl);
    }

    private void handleLambdaResult(VideoModerationLambdaResult result) throws JsonProcessingException {
        String jobId = result.getJobId();
        String status = result.getStatus();
        String objectKey = Optional.ofNullable(result.getObjectKey()).orElse(result.getJobTag());

        VideoModeration videoModeration = ensureVideoModeration(jobId, status, objectKey);
        if (videoModeration == null) {
            return;
        }

        List<ModerationLabel> labels = toModerationLabels(result.getLabels());
        String resultJson = result.getModerationResultJson();
        if (resultJson == null) {
            resultJson = objectMapper.writeValueAsString(result.getLabels());
        }

        String resolvedVideoUrl = resolveVideoUrl(videoModeration, objectKey);
        videoModerationProcessingService.handleResult(videoModeration, status, labels, resultJson, resolvedVideoUrl);
    }

    private VideoModeration ensureVideoModeration(String jobId, String status, String objectKey) {
        VideoModeration videoModeration = videoModerationRepository.findByJobId(jobId).orElse(null);
        if (videoModeration == null && objectKey != null) {
            videoModeration = videoModerationRepository.findByS3ObjectKey(objectKey).orElse(null);
        }

        if (videoModeration == null) {
            Post targetPost = resolvePostFromObjectKey(objectKey);
            if (targetPost == null) {
                log.error("Unable to locate post for moderation job. jobId={}, objectKey={}", jobId, objectKey);
                return null;
            }
            videoModeration = new VideoModeration();
            videoModeration.setJobId(jobId);
            videoModeration.setStatus(status);
            videoModeration.setPost(targetPost);
            videoModeration.setS3ObjectKey(objectKey);
            targetPost.setVideoModeration(videoModeration);
            videoModeration = videoModerationRepository.save(videoModeration);
            postRepository.save(targetPost);
            log.info("Created VideoModeration record for jobId={} and postId={}", jobId, targetPost.getId());
        } else {
            if (videoModeration.getS3ObjectKey() == null && objectKey != null) {
                videoModeration.setS3ObjectKey(objectKey);
            }
            videoModeration.setStatus(status);
            videoModerationRepository.save(videoModeration);
        }
        return videoModeration;
    }


    private GetContentModerationResult getModerationResult(String jobId) {
        GetContentModerationRequest request = new GetContentModerationRequest().withJobId(jobId);

        return amazonRekognition.getContentModeration(request);
    }

    private String extractObjectKey(RekognitionMessage message) {
        if (message.getVideo() != null && message.getVideo().getS3ObjectName() != null) {
            return message.getVideo().getS3ObjectName();
        }
        return Optional.ofNullable(message.getJobTag())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    private Post resolvePostFromObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        for (String candidate : s3Service.buildCandidateUrls(objectKey)) {
            List<Post> posts = postRepository.findByFileUrl(candidate);
            if (!posts.isEmpty()) {
                return posts.get(0);
            }
        }
        return null;
    }

    private String resolveVideoUrl(VideoModeration videoModeration, String objectKey) {
        Post post = videoModeration.getPost();
        if (objectKey != null) {
            for (String candidate : s3Service.buildCandidateUrls(objectKey)) {
                if (post.getFileUrls().contains(candidate)) {
                    return candidate;
                }
            }
        }
        return post.getFileUrls().stream().filter(this::isVideo).findFirst().orElse(null);
    }

    private List<ModerationLabel> toModerationLabels(List<VideoModerationLambdaResult.Label> labels) {
        if (labels == null || labels.isEmpty()) {
            return Collections.emptyList();
        }
        return labels.stream()
                .map(label -> new ModerationLabel()
                        .withName(label.getName())
                        .withParentName(label.getParentName())
                        .withConfidence(label.getConfidence() != null ? label.getConfidence() : 0f))
                .collect(Collectors.toList());
    }

    private boolean isVideo(String url) {
        if (url == null) {
            return false;
        }
        String lowerCaseUrl = url.toLowerCase();
        return lowerCaseUrl.endsWith(".mp4") || lowerCaseUrl.endsWith(".mov") || lowerCaseUrl.endsWith(".avi");
    }
}
