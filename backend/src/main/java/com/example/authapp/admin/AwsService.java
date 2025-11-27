package com.example.authapp.admin;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsRequest;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.example.authapp.admin.AdminReviewItem;
import com.example.authapp.admin.AdminReviewItem.ReviewStatus;
import com.example.authapp.config.AppProperties;
import com.example.authapp.image.ImageModeration;
import com.example.authapp.image.ImageModerationRepository;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostRepository;
import com.example.authapp.video.VideoModeration;
import com.example.authapp.video.VideoModerationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AwsService {

    private static final Logger log = LoggerFactory.getLogger(AwsService.class);

    private final AmazonS3 amazonS3;
    private final AmazonRekognition rekognition;
    private final String bucketName;
    private final PostRepository postRepository;
    private final ImageModerationRepository imageModerationRepository;
    private final AdminReviewItemRepository adminReviewItemRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final String awsRegion;
    private final VideoModerationRepository videoModerationRepository;

    @Value("${cloud.aws.cloudfront.domain:}")
    private String cloudfrontDomain;

    public AwsService(AmazonS3 amazonS3,
                      AmazonRekognition rekognition,
                      AppProperties appProperties,
                      PostRepository postRepository,
                      ImageModerationRepository imageModerationRepository,
                      AdminReviewItemRepository adminReviewItemRepository,
                      NotificationService notificationService,
                      ObjectMapper objectMapper,
                      VideoModerationRepository videoModerationRepository) {
        this.amazonS3 = amazonS3;
        this.rekognition = rekognition;
        this.bucketName = appProperties.getAws().getBucketName();
        this.awsRegion = appProperties.getAws().getRegion();
        this.postRepository = postRepository;
        this.imageModerationRepository = imageModerationRepository;
        this.adminReviewItemRepository = adminReviewItemRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.videoModerationRepository = videoModerationRepository;
        if (this.bucketName == null || this.bucketName.isEmpty()) {
            throw new IllegalStateException("AWS bucket name is not configured (app.aws.bucket-name)");
        }
    }

    public List<S3ObjectDto> listBucketObjects() {
        try {
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(bucketName)
                    .withMaxKeys(1000);
            return amazonS3.listObjectsV2(request)
                    .getObjectSummaries()
                    .stream()
                    .map(this::mapObject)
                    .sorted((a, b) -> b.lastModified().compareTo(a.lastModified()))
                    .collect(Collectors.toList());
        } catch (AmazonServiceException ex) {
            throw new AwsServiceException("Failed to list objects from S3", ex);
        }
    }

    @Transactional
    public List<RekognitionLabelDto> detectUnsafeImage(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return List.of();
        }

        try {
            if (isSupportedImage(objectKey)) {
                DetectModerationLabelsRequest request = new DetectModerationLabelsRequest()
                        .withImage(new Image().withS3Object(new S3Object()
                                .withBucket(bucketName)
                                .withName(objectKey)));

                List<ModerationLabel> labels = rekognition.detectModerationLabels(request)
                        .getModerationLabels();

                applyModerationResult(objectKey, labels);

                return labels.stream()
                        .map(this::mapLabel)
                        .collect(Collectors.toList());
            }

            if (isSupportedVideo(objectKey)) {
                return loadVideoModerationLabels(objectKey);
            }

            return List.of();
        } catch (AmazonServiceException ex) {
            throw new AwsServiceException("Failed to detect moderation labels", ex);
        }
    }

    private S3ObjectDto mapObject(S3ObjectSummary summary) {
        return new S3ObjectDto(summary.getKey(), summary.getSize(), summary.getLastModified().toInstant());
    }

    private RekognitionLabelDto mapLabel(ModerationLabel label) {
        return new RekognitionLabelDto(label.getName(), label.getConfidence());
    }

    private void applyModerationResult(String objectKey, List<ModerationLabel> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }

        String moderationJson = serializeLabels(labels);
        List<String> candidateUrls = buildCandidateUrls(objectKey);
        if (candidateUrls.isEmpty()) {
            return;
        }

        var postToMatchedUrls = candidateUrls.stream()
                .flatMap(url -> postRepository.findByFileUrl(url).stream()
                        .map(post -> java.util.Map.entry(post, url)))
                .collect(Collectors.groupingBy(java.util.Map.Entry::getKey,
                        Collectors.mapping(java.util.Map.Entry::getValue, Collectors.toList())));

        if (postToMatchedUrls.isEmpty()) {
            return;
        }

        for (var entry : postToMatchedUrls.entrySet()) {
            Post post = entry.getKey();
            List<String> matchedUrls = entry.getValue();
            AdminReviewItem itemForNotification = null;
            post.markReported(true);
            for (String matchedUrl : matchedUrls) {
                ImageModeration moderation = imageModerationRepository.findFirstByPostAndImageUrl(post, matchedUrl)
                        .orElseGet(() -> createModeration(post, matchedUrl));
                moderation.setInappropriate(true);
                moderation.setModerationResult(moderationJson);
                imageModerationRepository.save(moderation);

                AdminReviewItem reviewItem = adminReviewItemRepository
                        .findFirstByPostAndContentUrlAndReviewStatus(post, matchedUrl, ReviewStatus.PENDING)
                        .orElseGet(() -> createAdminReviewItem(post, matchedUrl));
                boolean isNewItem = reviewItem.getId() == null;
                reviewItem.setModerationResult(moderationJson);
                reviewItem.setInappropriateDetected(true);
                adminReviewItemRepository.save(reviewItem);
                if (isNewItem) {
                    itemForNotification = reviewItem;
                }
            }
            postRepository.save(post);
            if (itemForNotification != null) {
                notificationService.notifyAdmin(itemForNotification);
            }
        }
    }

    private ImageModeration createModeration(Post post, String imageUrl) {
        ImageModeration moderation = new ImageModeration();
        moderation.setPost(post);
        moderation.setImageUrl(imageUrl);
        return moderation;
    }

    private AdminReviewItem createAdminReviewItem(Post post, String contentUrl) {
        AdminReviewItem adminReviewItem = new AdminReviewItem();
        adminReviewItem.setPost(post);
        adminReviewItem.setContentUrl(contentUrl);
        adminReviewItem.setContentType("IMAGE");
        adminReviewItem.setReviewStatus(ReviewStatus.PENDING);
        return adminReviewItem;
    }

    private List<String> buildCandidateUrls(String objectKey) {
        Stream.Builder<String> builder = Stream.builder();

        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            builder.add(normalizeDomain(cloudfrontDomain) + "/" + objectKey);
        }

        if (bucketName != null && !bucketName.isBlank()) {
            String regionSegment = awsRegion != null && !awsRegion.isBlank() ? "." + awsRegion : "";
            builder.add(String.format("https://%s.s3%s.amazonaws.com/%s", bucketName, regionSegment, objectKey));
            if (awsRegion != null && !awsRegion.isBlank()) {
                builder.add(String.format("https://s3.%s.amazonaws.com/%s/%s", awsRegion, bucketName, objectKey));
            }
            builder.add(amazonS3.getUrl(bucketName, objectKey).toString());
        }

        return builder.build()
                .map(this::stripTrailingSlash)
                .distinct()
                .collect(Collectors.toList());
    }

    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv", "webm");

    private boolean isSupportedImage(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        String normalized = objectKey.toLowerCase(Locale.ROOT);
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0 || lastDot == normalized.length() - 1) {
            return false;
        }
        String extension = normalized.substring(lastDot + 1);
        return SUPPORTED_IMAGE_EXTENSIONS.contains(extension);
    }

    private boolean isSupportedVideo(String objectKey) {
        String normalized = objectKey.toLowerCase(Locale.ROOT);
        int lastDot = normalized.lastIndexOf('.');
        if (lastDot < 0 || lastDot == normalized.length() - 1) {
            return false;
        }
        String extension = normalized.substring(lastDot + 1);
        return SUPPORTED_VIDEO_EXTENSIONS.contains(extension);
    }

    private List<RekognitionLabelDto> loadVideoModerationLabels(String objectKey) {
        Optional<VideoModeration> moderationOpt = videoModerationRepository.findByS3ObjectKey(objectKey);
        if (moderationOpt.isEmpty()) {
            log.debug("No video moderation record found for objectKey={}", objectKey);
            return List.of();
        }

        String moderationJson = moderationOpt.get().getModerationResult();
        if (moderationJson == null || moderationJson.isBlank()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rawLabels = objectMapper.readValue(
                    moderationJson,
                    new TypeReference<List<Map<String, Object>>>() {});

            return rawLabels.stream()
                    .map(this::mapVideoLabel)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to parse moderation JSON for video key={}", objectKey, e);
            return List.of();
        }
    }

    private Optional<RekognitionLabelDto> mapVideoLabel(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }
        String name = asString(map.get("Name"));
        if (name == null) {
            name = asString(map.get("name"));
        }
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        Double confidence = asDouble(map.get("Confidence"));
        if (confidence == null) {
            confidence = asDouble(map.get("confidence"));
        }
        if (confidence == null) {
            confidence = 0.0;
        }

        return Optional.of(new RekognitionLabelDto(name, confidence));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeDomain(String domain) {
        String normalized = domain;
        if (!normalized.startsWith("http")) {
            normalized = "https://" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String serializeLabels(List<ModerationLabel> labels) {
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (JsonProcessingException e) {
            throw new AwsServiceException("Failed to serialize moderation labels", e);
        }
    }
}
