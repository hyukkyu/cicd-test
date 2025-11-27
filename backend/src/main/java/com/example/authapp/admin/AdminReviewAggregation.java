package com.example.authapp.admin;

import com.example.authapp.comment.Comment;
import com.example.authapp.post.Post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregates multiple AdminReviewItem records for the same post/comment so the UI can show
 * combined detection types and media.
 */
public record AdminReviewAggregation(
        String primaryContentType,
        String moderatedText,
        String moderationResult,
        boolean inappropriateDetected,
        String mediaUrl,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        Post post,
        Comment comment
) {

    public static AdminReviewAggregation aggregate(AdminReviewItem base, List<AdminReviewItem> siblings) {
        if (base == null) {
            return null;
        }
        List<AdminReviewItem> items = new ArrayList<>();
        items.add(base);
        if (siblings != null) {
            siblings.stream()
                    .filter(Objects::nonNull)
                    .filter(it -> !Objects.equals(it.getId(), base.getId()))
                    .forEach(items::add);
        }

        String mediaUrl = base.getContentUrl();
        String mediaType = base.getContentType();
        boolean inappropriate = base.isInappropriateDetected();
        String moderationResult = base.getModerationResult();
        String moderatedText = base.getModeratedText();
        LocalDateTime createdAt = base.getCreatedAt();
        LocalDateTime reviewedAt = base.getReviewedAt();
        List<String> moderationResults = new ArrayList<>();

        for (AdminReviewItem item : items) {
            if (!inappropriate && item.isInappropriateDetected()) {
                inappropriate = true;
            }
            if (item.getModerationResult() != null && (moderationResult == null || moderationResult.isBlank())) {
                moderationResult = item.getModerationResult();
            }
            if (item.getModerationResult() != null) {
                moderationResults.add(item.getModerationResult());
            }
            if (item.getModeratedText() != null && (moderatedText == null || moderatedText.isBlank())) {
                moderatedText = item.getModeratedText();
            }
            if ((mediaUrl == null || mediaUrl.isBlank()) && item.getContentUrl() != null) {
                mediaUrl = item.getContentUrl();
                mediaType = item.getContentType();
            }
            if (item.getCreatedAt() != null && (createdAt == null || item.getCreatedAt().isBefore(createdAt))) {
                createdAt = item.getCreatedAt();
            }
            if (item.getReviewedAt() != null && (reviewedAt == null || item.getReviewedAt().isAfter(reviewedAt))) {
                reviewedAt = item.getReviewedAt();
            }
        }

        String mergedModerationResult = moderationResults.isEmpty()
                ? moderationResult
                : String.join("\n\n---\n\n", moderationResults);

        return new AdminReviewAggregation(
                mediaType,
                moderatedText,
                mergedModerationResult,
                inappropriate,
                mediaUrl,
                createdAt,
                reviewedAt,
                base.getPost(),
                base.getComment()
        );
    }
}
