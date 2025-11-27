package com.example.authapp.video;

import com.amazonaws.services.rekognition.model.ModerationLabel;
import com.example.authapp.admin.AdminReviewItem;
import com.example.authapp.admin.AdminReviewItemRepository;
import com.example.authapp.admin.NotificationService;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VideoModerationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoModerationProcessingService.class);

    private final VideoModerationRepository videoModerationRepository;
    private final AdminReviewItemRepository adminReviewItemRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    public VideoModerationProcessingService(VideoModerationRepository videoModerationRepository,
                                            AdminReviewItemRepository adminReviewItemRepository,
                                            PostRepository postRepository,
                                            NotificationService notificationService) {
        this.videoModerationRepository = videoModerationRepository;
        this.adminReviewItemRepository = adminReviewItemRepository;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void handleResult(VideoModeration videoModeration,
                             String status,
                             List<ModerationLabel> labels,
                             String moderationResultJson,
                             String videoUrl) {
        String jobId = videoModeration.getJobId();
        log.info("Processing Rekognition result. jobId={}, status={}, labelCount={}", jobId, status, labels != null ? labels.size() : 0);
        videoModeration.setStatus(status);
        videoModeration.setModerationResult(moderationResultJson);
        videoModerationRepository.save(videoModeration);

        if (!"SUCCEEDED".equals(status)) {
            log.info("Skipping report processing because job did not succeed. jobId={}, status={}", jobId, status);
            return;
        }

        if (labels == null || labels.isEmpty()) {
            log.info("No moderation labels detected for jobId={}. Marking as clean.", jobId);
            return;
        }

        Post post = videoModeration.getPost();
        post.markReported(true);
        postRepository.save(post);
        log.warn("Marked post as reported due to video moderation findings. postId={}, jobId={}, labels={}", post.getId(), jobId, labels.size());

        AdminReviewItem reviewItem = new AdminReviewItem();
        reviewItem.setPost(post);
        reviewItem.setContentUrl(resolveVideoUrl(post, videoUrl));
        reviewItem.setContentType("VIDEO");
        reviewItem.setModerationResult(moderationResultJson);
        reviewItem.setInappropriateDetected(true);
        reviewItem.setReviewStatus(AdminReviewItem.ReviewStatus.PENDING);
        adminReviewItemRepository.save(reviewItem);
        log.info("Created admin review item for postId={}, reviewItemId={}", post.getId(), reviewItem.getId());

        notificationService.notifyAdmin(reviewItem);
        log.info("Admin notification dispatched for reviewItemId={}", reviewItem.getId());
    }

    private String resolveVideoUrl(Post post, String videoUrl) {
        if (videoUrl != null && !videoUrl.isBlank()) {
            return videoUrl;
        }
        return post.getFileUrls().stream()
                .filter(this::isVideo)
                .findFirst()
                .orElse("Unknown");
    }

    private boolean isVideo(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi");
    }
}
