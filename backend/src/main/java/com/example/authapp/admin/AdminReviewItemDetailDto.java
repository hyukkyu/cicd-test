package com.example.authapp.admin;

import com.example.authapp.comment.Comment;
import com.example.authapp.post.Post;
import java.time.LocalDateTime;

public record AdminReviewItemDetailDto(
        Long id,
        String contentType,
        String detectionType,
        java.util.List<String> detectionLabels,
        String contentUrl,
        String moderatedText,
        String moderationResult,
        boolean inappropriateDetected,
        String reviewStatus,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        PostInfo post,
        CommentInfo comment
) {

    public static AdminReviewItemDetailDto from(AdminReviewItem item) {
        return from(item, java.util.List.of(AdminReviewItemDto.resolveDetectionType(item)), java.util.List.of(item));
    }

    public static AdminReviewItemDetailDto from(AdminReviewItem item, java.util.List<String> detectionLabels, java.util.List<AdminReviewItem> siblings) {
        if (item == null) {
            return null;
        }
        AdminReviewAggregation aggregation = AdminReviewAggregation.aggregate(item, siblings);
        return new AdminReviewItemDetailDto(
                item.getId(),
                aggregation.primaryContentType(),
                AdminReviewItemDto.resolveDetectionType(item),
                detectionLabels,
                aggregation.mediaUrl(),
                aggregation.moderatedText(),
                aggregation.moderationResult(),
                aggregation.inappropriateDetected(),
                item.getReviewStatus() != null ? item.getReviewStatus().name() : null,
                aggregation.createdAt(),
                aggregation.reviewedAt(),
                PostInfo.from(aggregation.post()),
                CommentInfo.from(aggregation.comment())
        );
    }

    public record PostInfo(
            Long id,
            String title,
            String content,
            String authorName,
            Long authorId,
            LocalDateTime createdAt,
            String mainBoardName,
            String subBoardName,
            java.util.List<String> fileUrls
    ) {
        public static PostInfo from(Post post) {
            if (post == null) {
                return null;
            }
            return new PostInfo(
                    post.getId(),
                    post.getTitle(),
                    post.getContent(),
                    post.getAuthor() != null ? post.getAuthor().getUsername() : "알 수 없음",
                    post.getAuthor() != null ? post.getAuthor().getId() : null,
                    post.getCreateDate(),
                    post.getMainBoardName(),
                    post.getSubBoardName(),
                    post.getFileUrls() != null ? java.util.List.copyOf(post.getFileUrls()) : java.util.List.of()
            );
        }
    }

    public record CommentInfo(
            Long id,
            String content,
            String authorName,
            Long authorId,
            LocalDateTime createdAt
    ) {
        public static CommentInfo from(Comment comment) {
            if (comment == null) {
                return null;
            }
            return new CommentInfo(
                    comment.getId(),
                    comment.getContent(),
                    comment.getAuthor() != null ? comment.getAuthor().getUsername() : "알 수 없음",
                    comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                    comment.getCreateDate()
            );
        }
    }
}
