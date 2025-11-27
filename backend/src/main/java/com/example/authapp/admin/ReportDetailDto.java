package com.example.authapp.admin;

import com.example.authapp.comment.Comment;
import com.example.authapp.comment.CommentStatus;
import com.example.authapp.post.Post;
import com.example.authapp.report.Report;
import com.example.authapp.report.ReportAction;
import com.example.authapp.report.ReportStatus;
import com.example.authapp.report.ReportType;
import java.time.LocalDateTime;

public record ReportDetailDto(
        Long id,
        ReportType type,
        ReportStatus status,
        ReportAction action,
        String reason,
        String adminNote,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        String reporterName,
        String reporterUsername,
        PostInfo post,
        CommentInfo comment
) {
    public static ReportDetailDto from(Report report, Post post, Comment comment, String reporterName, String reporterUsername) {
        return new ReportDetailDto(
                report.getId(),
                report.getType(),
                report.getStatus(),
                report.getAction(),
                report.getReason(),
                report.getAdminNote(),
                report.getCreateDate(),
                report.getProcessedAt(),
                reporterName,
                reporterUsername,
                post != null ? PostInfo.from(post) : null,
                comment != null ? CommentInfo.from(comment) : null
        );
    }

    public record PostInfo(
            Long id,
            String title,
            String content,
            String authorUsername,
            Long authorId,
            String mainBoardName,
            String subBoardName,
            LocalDateTime createdAt
    ) {
        public static PostInfo from(Post post) {
            return new PostInfo(
                    post.getId(),
                    post.getTitle(),
                    post.getContent(),
                    post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                    post.getAuthor() != null ? post.getAuthor().getId() : null,
                    post.getMainBoardName(),
                    post.getSubBoardName(),
                    post.getCreateDate()
            );
        }
    }

    public record CommentInfo(
            Long id,
            String content,
            String authorUsername,
            Long authorId,
            CommentStatus status,
            LocalDateTime createdAt,
            Long postId
    ) {
        public static CommentInfo from(Comment comment) {
            return new CommentInfo(
                    comment.getId(),
                    comment.getContent(),
                    comment.getAuthor() != null ? comment.getAuthor().getUsername() : null,
                    comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                    comment.getStatus(),
                    comment.getCreateDate(),
                    comment.getPost() != null ? comment.getPost().getId() : null
            );
        }
    }
}
