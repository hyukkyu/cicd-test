package com.example.authapp.post;

import com.amazonaws.services.comprehend.AmazonComprehend;
import com.amazonaws.services.comprehend.model.DetectPiiEntitiesRequest;
import com.amazonaws.services.comprehend.model.DetectPiiEntitiesResult;
import com.amazonaws.services.comprehend.model.DetectSentimentRequest;
import com.amazonaws.services.comprehend.model.DetectSentimentResult;
import com.amazonaws.services.comprehend.model.PiiEntity;
import com.amazonaws.services.comprehend.model.SentimentScore;
import com.amazonaws.services.comprehend.model.AmazonComprehendException;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.model.AmazonRekognitionException;
import com.amazonaws.services.rekognition.model.ContentModerationDetection;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectModerationLabelsResult;
import com.amazonaws.services.rekognition.model.GetContentModerationRequest;
import com.amazonaws.services.rekognition.model.GetContentModerationResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.ModerationLabel;
import com.amazonaws.services.rekognition.model.S3Object;
import com.amazonaws.services.rekognition.model.StartContentModerationRequest;
import com.amazonaws.services.rekognition.model.StartContentModerationResult;
import com.amazonaws.services.rekognition.model.Video;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;
import com.example.authapp.admin.AdminReviewItem;
import com.example.authapp.admin.AdminReviewItemRepository;
import com.example.authapp.admin.NotificationService;
import com.example.authapp.image.ImageModeration;
import com.example.authapp.image.ImageModerationRepository;
import com.example.authapp.user.User;
import com.example.authapp.video.VideoModeration;
import com.example.authapp.video.VideoModerationRepository;
import com.example.authapp.video.VideoModeration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    private static final Set<String> COMPREHEND_PII_SUPPORTED_LANGUAGES = Set.of("en", "es");

    private final PostRepository postRepository;
        private final AmazonRekognition amazonRekognition;
        private final AmazonComprehend amazonComprehend;
        private final AmazonTranslate amazonTranslate;
    private final ImageModerationRepository imageModerationRepository;
    private final AdminReviewItemRepository adminReviewItemRepository;
    private final VideoModerationRepository videoModerationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${cloud.aws.sns.topic.arn}")
    private String snsTopicArn;

    @Value("${cloud.aws.iam.role.arn}")
    private String roleArn;

    @Value("${cloud.aws.s3.bucket}")
    private String s3BucketName;

    @Value("${cloud.aws.cloudfront.domain:}")
    private String cloudfrontDomain;

    @Value("${app.moderation.text-language-code:ko}")
    private String textModerationLanguageCode;

    public PostService(PostRepository postRepository,
                       AmazonRekognition amazonRekognition,
                       AmazonComprehend amazonComprehend,
                       AmazonTranslate amazonTranslate,
                       ImageModerationRepository imageModerationRepository,
                       AdminReviewItemRepository adminReviewItemRepository,
                       NotificationService notificationService,
                       VideoModerationRepository videoModerationRepository,
                       ObjectMapper objectMapper) {
        this.postRepository = postRepository;
        this.amazonRekognition = amazonRekognition;
        this.amazonComprehend = amazonComprehend;
        this.amazonTranslate = amazonTranslate;
        this.imageModerationRepository = imageModerationRepository;
        this.adminReviewItemRepository = adminReviewItemRepository;
        this.notificationService = notificationService;
        this.videoModerationRepository = videoModerationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Post create(String title, String content, String mainBoardName, String subBoardName, String tabItem, User author, List<String> fileUrls) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent(content);
        post.setMainBoardName(mainBoardName);
        post.setSubBoardName(subBoardName);
        post.setTabItem(tabItem);
        post.setAuthor(author);
        post.setFileUrls(fileUrls);
        post.setCreateDate(java.time.LocalDateTime.now());

        List<TextModerationResult> textModerationResults = moderateTextContent(post, title, content);

        Post saved = this.postRepository.save(post);

        if (fileUrls != null && !fileUrls.isEmpty()) {
            for (String url : fileUrls) {
                if (isVideo(url)) {
                    log.debug("Detected video attachment for new post, running moderation synchronously. url={}", url);
                    moderateVideo(saved, url);
                } else if (isImage(url)) {
                    log.debug("Detected image attachment for new post, running moderation synchronously. url={}", url);
                    moderateImage(saved, url);
                }
            }
        }

        if (textModerationResults != null) {
            for (TextModerationResult result : textModerationResults) {
                if (result != null && result.outcome().shouldNotify()) {
                    createAdminReviewItemForText(saved, result);
                }
            }
        }

        return saved;
    }

    private void moderateImage(Post post, String imageUrl) {
        String bucket = extractBucketName(imageUrl);
        String key = extractKey(imageUrl);

        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            log.warn("Skipping image moderation due to missing bucket/key. url={}, bucket={}, key={}", imageUrl, bucket, key);
            return;
        }

        DetectModerationLabelsRequest request = new DetectModerationLabelsRequest()
                .withImage(new Image().withS3Object(new com.amazonaws.services.rekognition.model.S3Object().withBucket(bucket).withName(key)))
                .withMinConfidence(75F);

        try {
            log.info("Running image moderation for post={}, bucket={}, key={}", post.getId(), bucket, key);
            DetectModerationLabelsResult result = amazonRekognition.detectModerationLabels(request);
            List<ModerationLabel> labels = result.getModerationLabels();
            log.info("Image moderation completed. postId={}, key={}, detectedLabels={}", post.getId(), key, labels.size());

            ImageModeration imageModeration = new ImageModeration();
            imageModeration.setImageUrl(imageUrl);
            imageModeration.setPost(post);
            imageModeration.setInappropriate(!labels.isEmpty());
            imageModeration.setModerationResult(objectMapper.writeValueAsString(labels));

            post.getImageModerations().add(imageModeration);

            if (imageModeration.isInappropriate()) {
                log.warn("Inappropriate content detected in image. postId={}, key={}, labels={}", post.getId(), key, labels.size());
                post.markReported(true);
                AdminReviewItem adminReviewItem = new AdminReviewItem();
                adminReviewItem.setPost(post);
                adminReviewItem.setContentUrl(imageUrl);
                adminReviewItem.setContentType("IMAGE"); // Fixed: Use String literal
                adminReviewItem.setModerationResult(imageModeration.getModerationResult());
                adminReviewItem.setInappropriateDetected(true);
                adminReviewItem.setReviewStatus(AdminReviewItem.ReviewStatus.PENDING);
                adminReviewItemRepository.save(adminReviewItem);
                notificationService.notifyAdmin(adminReviewItem);
            }

        } catch (AmazonRekognitionException | JsonProcessingException e) {
            log.error("Error moderating image for postId={}, url={}", post.getId(), imageUrl, e);
        }
    }

    private void moderateVideo(Post post, String videoUrl) {
        String bucket = extractBucketName(videoUrl);
        String key = extractKey(videoUrl);

        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            log.warn("Skipping video moderation due to missing bucket/key. url={}, bucket={}, key={}", videoUrl, bucket, key);
            return;
        }

        StartContentModerationRequest request = new StartContentModerationRequest()
                .withVideo(new Video()
                        .withS3Object(new com.amazonaws.services.rekognition.model.S3Object()
                                .withBucket(bucket)
                                .withName(key)))
                .withMinConfidence(75F);

        try {
            log.info("Running video moderation for post={}, bucket={}, key={}", post.getId(), bucket, key);
            StartContentModerationResult startResult = amazonRekognition.startContentModeration(request);
            String jobId = startResult.getJobId();
            List<ModerationLabel> labels = collectVideoModerationLabels(jobId);
            VideoModeration videoModeration = post.getVideoModeration();
            if (videoModeration == null) {
                videoModeration = new VideoModeration();
                videoModeration.setPost(post);
                post.setVideoModeration(videoModeration);
            }
            videoModeration.setJobId(jobId);
            videoModeration.setS3ObjectKey(key);
            videoModeration.setStatus("SUCCEEDED");
            videoModeration.setModerationResult(objectMapper.writeValueAsString(labels));
            videoModerationRepository.save(videoModeration);

            if (!labels.isEmpty()) {
                log.warn("Inappropriate content detected in video. postId={}, key={}, labels={}", post.getId(), key, labels.size());
                post.markReported(true);
                AdminReviewItem adminReviewItem = new AdminReviewItem();
                adminReviewItem.setPost(post);
                adminReviewItem.setContentUrl(videoUrl);
                adminReviewItem.setContentType("VIDEO");
                adminReviewItem.setModerationResult(videoModeration.getModerationResult());
                adminReviewItem.setInappropriateDetected(true);
                adminReviewItem.setReviewStatus(AdminReviewItem.ReviewStatus.PENDING);
                adminReviewItemRepository.save(adminReviewItem);
                notificationService.notifyAdmin(adminReviewItem);
            }
        } catch (AmazonRekognitionException | JsonProcessingException e) {
            log.error("Error moderating video for postId={}, url={}", post.getId(), videoUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Video moderation interrupted for postId={}, url={}", post.getId(), videoUrl, e);
        }
    }

    private List<ModerationLabel> collectVideoModerationLabels(String jobId) throws InterruptedException {
        List<ModerationLabel> labels = new ArrayList<>();
        String nextToken = null;

        while (true) {
            GetContentModerationRequest request = new GetContentModerationRequest()
                    .withJobId(jobId)
                    .withMaxResults(200)
                    .withNextToken(nextToken);
            GetContentModerationResult result = amazonRekognition.getContentModeration(request);
            String status = result.getJobStatus();
            if ("IN_PROGRESS".equals(status)) {
                TimeUnit.SECONDS.sleep(5);
                continue;
            }
            if (!"SUCCEEDED".equals(status) && !"PARTIAL_SUCCESS".equals(status)) {
                throw new AmazonRekognitionException("Video moderation job failed. jobId=" + jobId + ", status=" + status);
            }
            List<ContentModerationDetection> detections = result.getModerationLabels();
            if (detections != null) {
                for (ContentModerationDetection detection : detections) {
                    if (detection != null && detection.getModerationLabel() != null) {
                        labels.add(detection.getModerationLabel());
                    }
                }
            }
            nextToken = result.getNextToken();
            if (nextToken == null || nextToken.isBlank()) {
                break;
            }
        }
        return labels;
    }

    private List<TextModerationResult> moderateTextContent(Post post, String title, String content) {
        TextModerationOutcome titleOutcome = moderateText(title);
        TextModerationOutcome contentOutcome = moderateText(content);

        logTextModerationResult("TITLE", titleOutcome);
        logTextModerationResult("CONTENT", contentOutcome);

        boolean harmfulDetected = titleOutcome.isBlocked() || contentOutcome.isBlocked();
        boolean shouldNotify = titleOutcome.shouldNotify() || contentOutcome.shouldNotify();
        boolean reviewCandidate = !harmfulDetected && shouldNotify;

        post.setBlocked(false);

        if (harmfulDetected) {
            post.markReported(true);
        } else {
            post.setHarmful(false);
            post.setModerationStatus(reviewCandidate ? PostModerationStatus.REVIEW : PostModerationStatus.VISIBLE);
        }

        List<TextModerationResult> results = new ArrayList<>(2);
        results.add(new TextModerationResult("TITLE", title, titleOutcome));
        results.add(new TextModerationResult("CONTENT", content, contentOutcome));
        return results;
    }

    private TextModerationOutcome moderateText(String text) {
        if (text == null || text.isBlank()) {
            return new TextModerationOutcome(false, false, false, 0.0, "", null, null);
        }

        String trimmedText = text.length() > 4500 ? text.substring(0, 4500) : text;
        String languageCode = resolveTextLanguageCode();

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

    private void logTextModerationResult(String componentLabel, TextModerationOutcome outcome) {
        if (outcome == null) {
            log.info("Text moderation skipped for component='{}' (no outcome)", componentLabel);
            return;
        }

        log.info("Text moderation result component='{}' blocked={} review={} negativeScore={} piiDetected={} summary='{}'",
                componentLabel,
                outcome.isBlocked(),
                outcome.isReview(),
                outcome.getNegativeScore(),
                outcome.isPiiDetected(),
                outcome.getSummary());

        if (outcome.shouldNotify()) {
            log.warn("Potentially harmful text detected for component='{}' (blocked={}, review={})",
                    componentLabel,
                    outcome.isBlocked(),
                    outcome.isReview());
        }
    }

    private String buildTextSummary(double negativeScore, String dominantSentiment, int piiCount, boolean piiScanAttempted) {
        String summary = String.format("sentiment=%s, negative=%.3f, piiCount=%d", dominantSentiment, negativeScore, piiCount);
        if (!piiScanAttempted) {
            summary += " (pii-scan-skipped)";
        }
        return summary;
    }

    private void createAdminReviewItemForText(Post post, TextModerationResult result) {
        if (result == null || result.outcome() == null) {
            log.warn("Skipping admin review item creation for postId={} due to missing moderation result.", post != null ? post.getId() : null);
            return;
        }

        AdminReviewItem adminReviewItem = new AdminReviewItem();
        adminReviewItem.setPost(post);
        adminReviewItem.setContentType("TEXT");
        String originalText = result.originalText() != null ? result.originalText() : "";
        adminReviewItem.setModeratedText("[" + result.component() + "] " + originalText);
        adminReviewItem.setModerationResult(result.outcome().serializeModerationResult(objectMapper));
        adminReviewItem.setInappropriateDetected(true);
        adminReviewItem.setReviewStatus(AdminReviewItem.ReviewStatus.PENDING);
        adminReviewItemRepository.save(adminReviewItem);
        log.warn("Created admin review item for postId={} component='{}'", post.getId(), result.component());
        notificationService.notifyAdmin(adminReviewItem);
    }

    private record TextModerationResult(String component, String originalText, TextModerationOutcome outcome) {}

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String resolveTextLanguageCode() {
        if (textModerationLanguageCode == null || textModerationLanguageCode.isBlank()) {
            return "ko";
        }
        return textModerationLanguageCode.trim().toLowerCase(Locale.ROOT);
    }

    private boolean supportsPiiDetection(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return false;
        }
        return COMPREHEND_PII_SUPPORTED_LANGUAGES.contains(languageCode.toLowerCase(Locale.ROOT));
    }

    private boolean isVideo(String url) {
        if (url == null) return false;
        String lowerCaseUrl = url.toLowerCase();
        return lowerCaseUrl.endsWith(".mp4") || lowerCaseUrl.endsWith(".mov") || lowerCaseUrl.endsWith(".avi");
    }

    private boolean isImage(String url) {
        if (url == null) return false;
        String lowerCaseUrl = url.toLowerCase();
        return lowerCaseUrl.endsWith(".jpg") || lowerCaseUrl.endsWith(".jpeg") || lowerCaseUrl.endsWith(".png");
    }

    private String extractBucketName(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            String host = parsedUrl.getHost();
            if (host == null) {
                return s3BucketName;
            }

            String normalizedCloudfront = cloudfrontDomain;
            if (normalizedCloudfront != null && !normalizedCloudfront.isBlank()) {
                normalizedCloudfront = normalizedCloudfront.replaceFirst("^https?://", "");
                if (normalizedCloudfront.endsWith("/")) {
                    normalizedCloudfront = normalizedCloudfront.substring(0, normalizedCloudfront.length() - 1);
                }
                if (host.equalsIgnoreCase(normalizedCloudfront)) {
                    return s3BucketName;
                }
            }

            int dotIndex = host.indexOf('.');
            if (dotIndex > 0) {
                return host.substring(0, dotIndex);
            }
            return host;
        } catch (java.net.MalformedURLException e) {
            return s3BucketName;
        }
    }

    private String extractKey(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            return parsedUrl.getPath().substring(1);
        } catch (java.net.MalformedURLException e) {
            return null;
        }
    }

    private Specification<Post> search(String searchType, String kw) {
        return (root, query, cb) -> {
            query.distinct(true);
            if (kw == null || kw.trim().isEmpty()) {
                return cb.conjunction();
            }

            switch (searchType) {
                case "title":
                    return cb.like(root.get("title"), "%" + kw + "%");
                case "content":
                    return cb.like(root.get("content"), "%" + kw + "%");
                case "user":
                    Join<Post, User> u1 = root.join("author", JoinType.LEFT);
                    return cb.like(u1.get("username"), "%" + kw + "%");
                default: // "all"
                    Join<Post, User> u2 = root.join("author", JoinType.LEFT);
                    return cb.or(
                        cb.like(root.get("title"), "%" + kw + "%"),
                        cb.like(root.get("content"), "%" + kw + "%"),
                        cb.like(u2.get("username"), "%" + kw + "%")
                    );
            }
        };
    }

    private Specification<Post> popularFilter() {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(cb.size(root.get("voter")), 1);
    }

    private Specification<Post> visibleOnly() {
        return (root, query, cb) -> cb.or(
            cb.equal(root.get("moderationStatus"), PostModerationStatus.VISIBLE),
            cb.equal(root.get("moderationStatus"), PostModerationStatus.REVIEW)
        );
    }

    public Page<Post> getList(int page, String searchType, String kw) {
        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.desc("createDate"));
        Pageable pageable = PageRequest.of(page, 15, Sort.by(sorts));
        Specification<Post> spec = search(searchType, kw).and(visibleOnly());
        return this.postRepository.findAll(spec, pageable);
    }

    public Page<Post> getListByCategory(String subBoardName, int page, String searchType, String kw, boolean isPopular) {
        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.desc("createDate"));
        Pageable pageable = PageRequest.of(page, 15, Sort.by(sorts));
        Specification<Post> spec = (root, query, cb) -> cb.equal(root.get("subBoardName"), subBoardName);
        spec = spec.and(search(searchType, kw));
        spec = spec.and(visibleOnly());
        if (isPopular) {
            spec = spec.and(popularFilter());
        }
        return this.postRepository.findAll(spec, pageable);
    }

    public Page<Post> getListByCategoryAndTab(String subBoardName, String tabItem, int page, String searchType, String kw, boolean isPopular) {
        List<Sort.Order> sorts = new ArrayList<>();
        sorts.add(Sort.Order.desc("createDate"));
        Pageable pageable = PageRequest.of(page, 15, Sort.by(sorts));
        Specification<Post> spec = (root, query, cb) -> {
            Predicate p1 = cb.equal(root.get("subBoardName"), subBoardName);
            Predicate p2 = cb.equal(root.get("tabItem"), tabItem);
            return cb.and(p1, p2);
        };
        spec = spec.and(search(searchType, kw));
        spec = spec.and(visibleOnly());
        if (isPopular) {
            spec = spec.and(popularFilter());
        }
        return this.postRepository.findAll(spec, pageable);
    }

    public Page<Post> getGalleryPosts(GalleryQuery query) {
        List<Sort.Order> orders = new ArrayList<>();
        if ("popular".equalsIgnoreCase(query.sort())) {
            orders.add(Sort.Order.desc("viewCount"));
        }
        orders.add(Sort.Order.desc("createDate"));
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by(orders));

        Specification<Post> spec = visibleOnly();
        if (StringUtils.hasText(query.mainBoardName())) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("mainBoardName"), query.mainBoardName()));
        }
        if (StringUtils.hasText(query.subBoardName())) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("subBoardName"), query.subBoardName()));
        }
        if (StringUtils.hasText(query.tabItem()) && !"전체".equals(query.tabItem())) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tabItem"), query.tabItem()));
        }
        spec = spec.and(search(query.searchType(), query.keyword()));

        return postRepository.findAll(spec, pageable);
    }

    public Post getPost(Long id) {
        Optional<Post> post = this.postRepository.findById(id);
        if (post.isPresent()) {
            Post post1 = post.get();
            post1.setViewCount(post1.getViewCount() + 1);
            this.postRepository.save(post1);
            return post1;
        } else {
            throw new RuntimeException("Post not found"); // Or a custom exception
        }
    }

    public Post getPostWithoutIncrement(Long id) {
        return this.postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    public Post getPostForEdit(Long id) {
        return this.postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
    }

    @Transactional
    public void update(Post post, String title, String content, String mainBoardName, String subBoardName, String tabItem, List<String> fileUrls) {
        post.setTitle(title);
        post.setContent(content);
        post.setMainBoardName(mainBoardName);
        post.setSubBoardName(subBoardName);
        post.setTabItem(tabItem);
        post.getFileUrls().clear();
        post.getFileUrls().addAll(fileUrls);
        this.postRepository.save(post);
    }

    public void delete(Post post) {
        this.postRepository.delete(post);
    }

    public void vote(Post post, User user) {
        if (post.getVoter().contains(user)) {
            post.getVoter().remove(user);
        }
        else {
            post.getVoter().add(user);
        }
        this.postRepository.save(post);
    }

    public List<Post> getPopularList(int count) {
        List<Post> allPosts = this.postRepository.findAll().stream()
                .filter(post -> post.getModerationStatus() == PostModerationStatus.VISIBLE)
                .collect(Collectors.toList());
        return allPosts.stream()
                .sorted((p1, p2) -> Integer.compare(p2.getVoter().size(), p1.getVoter().size()))
                .limit(count)
                .collect(Collectors.toList());
    }

    public List<Post> getPopularGalleryPosts(int count) {
        Pageable pageable = PageRequest.of(0, count);
        List<String> galleryMainBoards = List.of("game", "travel", "exercise", "movie", "music", "invest");
        Page<Post> popularPostsPage = this.postRepository.findPopularPostsInMainBoards(galleryMainBoards, 1, pageable);
        return popularPostsPage.getContent().stream()
                .filter(post -> post.getModerationStatus() == PostModerationStatus.VISIBLE)
                .collect(Collectors.toList());
    }

    public List<Post> getRecentList() {
        List<Post> latest = this.postRepository.findTop10ByOrderByCreateDateDesc();
        List<Post> ordered = new ArrayList<>();

        latest.stream()
                .filter(post -> post.isHarmful() && post.getModerationStatus() != PostModerationStatus.BLOCKED)
                .sorted((p1, p2) -> {
                    LocalDateTime d1 = p1.getCreateDate();
                    LocalDateTime d2 = p2.getCreateDate();
                    if (d1 == null && d2 == null) {
                        return 0;
                    }
                    if (d1 == null) {
                        return 1;
                    }
                    if (d2 == null) {
                        return -1;
                    }
                    return d2.compareTo(d1);
                })
                .forEach(ordered::add);

        latest.stream()
                .filter(post -> !post.isHarmful() && post.getModerationStatus() == PostModerationStatus.VISIBLE)
                .forEach(post -> {
                    if (!ordered.contains(post)) {
                        ordered.add(post);
                    }
                });

        return ordered.size() > 10 ? ordered.subList(0, 10) : ordered;
    }

    public java.util.Map<String, Page<Post>> searchGalleryAndFreeboard(String keyword, int galleryPage, int freeboardPage) {
        Pageable galleryPageable = PageRequest.of(galleryPage, 5, Sort.by(Sort.Order.desc("createDate"))); // 5 items per page for gallery
        Pageable freeboardPageable = PageRequest.of(freeboardPage, 10, Sort.by(Sort.Order.desc("createDate"))); // 10 items per page for freeboard

        Specification<Post> gallerySpec;
        List<String> galleryMainBoards = java.util.Arrays.asList("game", "travel", "exercise", "movie", "music", "invest");
        gallerySpec = (root, query, cb) -> {
            Predicate mainBoardPredicate = root.get("mainBoardName").in(galleryMainBoards);
            Predicate keywordPredicate = cb.or(
                cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("content")), "%" + keyword.toLowerCase() + "%")
            );
            Predicate visibilityPredicate = cb.equal(root.get("moderationStatus"), PostModerationStatus.VISIBLE);
            return cb.and(mainBoardPredicate, keywordPredicate, visibilityPredicate);
        };

        Specification<Post> freeboardSpec;
        freeboardSpec = (root, query, cb) -> {
            Predicate subBoardPredicate = cb.equal(root.get("subBoardName"), "자유");
            Predicate keywordPredicate = cb.or(
                cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("content")), "%" + keyword.toLowerCase() + "%")
            );
            Predicate visibilityPredicate = cb.equal(root.get("moderationStatus"), PostModerationStatus.VISIBLE);
            return cb.and(subBoardPredicate, keywordPredicate, visibilityPredicate);
        };

        Page<Post> galleryPosts = this.postRepository.findAll(gallerySpec, galleryPageable);
        Page<Post> freeboardPosts = this.postRepository.findAll(freeboardSpec, freeboardPageable);

        java.util.Map<String, Page<Post>> results = new java.util.HashMap<>();
        results.put("gallery", galleryPosts);
        results.put("freeboard", freeboardPosts);

        return results;
    }

    public List<String> getTabItemsForCategory(String mainBoardName) {
        if (mainBoardName == null) {
            return java.util.Arrays.asList("일반", "질문");
        }
        switch (mainBoardName) {
            case "game":
                return java.util.Arrays.asList("일반", "질문", "파티", "창작", "그림");
            case "exercise":
                return java.util.Arrays.asList("일반", "질문", "장비", "대회", "꿀팁", "모집");
            case "movie":
                return java.util.Arrays.asList("일반", "질문", "개봉정보", "후기", "스포", "모집");
            case "music":
                return java.util.Arrays.asList("일반", "질문", "추천", "정보");
            case "travel":
                return java.util.Arrays.asList("일반", "질문", "정보", "후기", "사진", "모집");
            case "invest":
                return java.util.Arrays.asList("전체", "정보", "뉴스");
            default:
                return java.util.Arrays.asList("일반", "질문");
        }
    }

    public List<String> getUniqueSubBoardNamesForMainBoard(String mainBoardName) {
        return postRepository.findDistinctSubBoardNameByMainBoardName(mainBoardName);
    }

    public List<Post> findByAuthor(User author) {
        return postRepository.findByAuthor(author);
    }

    public record GalleryQuery(
            String mainBoardName,
            String subBoardName,
            String tabItem,
            String sort,
            String searchType,
            String keyword,
            int page,
            int size
    ) {
    }
}
