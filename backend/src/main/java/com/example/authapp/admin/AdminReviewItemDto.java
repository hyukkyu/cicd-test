package com.example.authapp.admin;

import java.time.LocalDateTime;

public record AdminReviewItemDto(
        Long id,
        String contentType,
        String detectionType,
        String detectionComponent,
        String title,
        String excerpt,
        String contentUrl,
        String moderationResult,
        boolean inappropriateDetected,
        Long postId,
        Long commentId,
        String authorName,
        String mainBoardName,
        String subBoardName,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String reviewStatus
) {
    public static AdminReviewItemDto from(AdminReviewItem item) {
        if (item == null) {
            return null;
        }
        return new AdminReviewItemDto(
                item.getId(),
                item.getContentType(),
                resolveDetectionType(item),
                resolveComponent(item),
                resolveTitle(item),
                resolveExcerpt(item),
                item.getContentUrl(),
                item.getModerationResult(),
                item.isInappropriateDetected(),
                item.getPost() != null ? item.getPost().getId() : null,
                item.getComment() != null ? item.getComment().getId() : null,
                resolveAuthorName(item),
                resolveMainBoard(item),
                resolveSubBoard(item),
                item.getCreatedAt(),
                item.getReviewedAt(),
                item.getReviewStatus() != null ? item.getReviewStatus().name() : null
        );
    }

    static String resolveDetectionType(AdminReviewItem item) {
        String component = resolveComponent(item);
        if (component != null) {
            return component;
        }
        if (item.getComment() != null) {
            return "댓글";
        }
        String type = item.getContentType();
        if (type == null) {
            return "미상";
        }
        return switch (type.toUpperCase()) {
            case "IMAGE" -> "이미지";
            case "VIDEO" -> "동영상";
            case "TITLE" -> "제목";
            case "BODY", "TEXT" -> "본문";
            default -> type;
        };
    }

    static String resolveComponent(AdminReviewItem item) {
        String text = item.getModeratedText();
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\[([^\\]]+)\\]").matcher(text);
        if (m.find()) {
            String comp = m.group(1).toUpperCase();
            return switch (comp) {
                case "TITLE" -> "제목";
                case "BODY", "TEXT" -> "본문";
                default -> comp;
            };
        }
        return null;
    }

    private static String resolveAuthorName(AdminReviewItem item) {
        if (item.getComment() != null && item.getComment().getAuthor() != null) {
            return item.getComment().getAuthor().getUsername();
        }
        if (item.getPost() != null && item.getPost().getAuthor() != null) {
            return item.getPost().getAuthor().getUsername();
        }
        return "알 수 없음";
    }

    private static String resolveMainBoard(AdminReviewItem item) {
        if (item.getPost() != null) {
            return item.getPost().getMainBoardName();
        }
        if (item.getComment() != null && item.getComment().getPost() != null) {
            return item.getComment().getPost().getMainBoardName();
        }
        return null;
    }

    private static String resolveTitle(AdminReviewItem item) {
        if (item.getPost() != null) {
            return item.getPost().getTitle();
        }
        if (item.getComment() != null) {
            return "(댓글)";
        }
        return "(제목 없음)";
    }

    private static String resolveExcerpt(AdminReviewItem item) {
        String content = null;
        if (item.getPost() != null) {
            content = item.getPost().getContent();
        } else if (item.getComment() != null) {
            content = item.getComment().getContent();
        }
        if (content == null || content.isBlank()) {
            return null;
        }
        String plain = content.replaceAll("\\s+", " ").trim();
        return plain.length() > 120 ? plain.substring(0, 120) + "…" : plain;
    }

    private static String resolveSubBoard(AdminReviewItem item) {
        if (item.getPost() != null) {
            return item.getPost().getSubBoardName();
        }
        if (item.getComment() != null && item.getComment().getPost() != null) {
            return item.getComment().getPost().getSubBoardName();
        }
        return null;
    }
}
