package com.example.authapp.admin;

import com.example.authapp.image.ImageModeration;
import com.example.authapp.video.VideoModeration;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPostDetailDto(
        Long id,
        String title,
        String content,
        String mainBoardName,
        String subBoardName,
        String tabItem,
        com.example.authapp.post.PostStatus status,
        com.example.authapp.post.PostModerationStatus moderationStatus,
        boolean blocked,
        boolean harmful,
        boolean publishedAfterHarmful,
        LocalDateTime createdAt,
        int viewCount,
        Long authorId,
        String authorUsername,
        List<String> fileUrls,
        List<CommentView> comments,
        List<ImageModerationView> imageModerations,
        TextModerationView textModeration,
        VideoModerationView videoModeration
) {

    public record CommentView(Long id, Long authorId, String authorUsername, String content, boolean blocked, LocalDateTime createdAt) {
    }

    public record ImageModerationView(String imageUrl, boolean inappropriateDetected, String moderationResult) {
        public static ImageModerationView from(ImageModeration moderation) {
            return new ImageModerationView(
                    moderation.getImageUrl(),
                    moderation.isInappropriate(),
                    moderation.getModerationResult()
            );
        }
    }

    public record VideoModerationView(String status, String moderationResult) {
        public static VideoModerationView from(VideoModeration moderation) {
            if (moderation == null) {
                return null;
            }
            return new VideoModerationView(
                    moderation.getStatus(),
                    moderation.getModerationResult()
            );
        }
    }

    public record TextModerationView(String dominantSentiment,
                                     double negativeScore,
                                     boolean piiDetected,
                                     String summary,
                                     String sentimentScoresJson,
                                     String piiEntitiesJson,
                                     LocalDateTime createdAt) {
        public static TextModerationView from(com.example.authapp.post.TextModeration moderation) {
            if (moderation == null) {
                return null;
            }
            return new TextModerationView(
                    moderation.getDominantSentiment(),
                    moderation.getNegativeScore(),
                    moderation.isPiiDetected(),
                    moderation.getSummary(),
                    moderation.getSentimentScoresJson(),
                    moderation.getPiiEntitiesJson(),
                    moderation.getCreatedAt()
            );
        }
    }
}
