package com.example.authapp.post.dto;

import com.example.authapp.comment.Comment;
import com.example.authapp.comment.CommentStatus;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        String authorUsername,
        String authorNickname,
        Long parentId,
        LocalDateTime createdAt,
        CommentStatus status
) {
    public static CommentResponse from(Comment comment) {
        String content = comment.getStatus() == CommentStatus.BLOCKED
                ? "관리자에 의해 차단되었습니다."
                : comment.getContent();
        return new CommentResponse(
                comment.getId(),
                content,
                comment.getAuthor() != null ? comment.getAuthor().getUsername() : null,
                comment.getAuthor() != null ? comment.getAuthor().getNickname() : null,
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getCreateDate(),
                comment.getStatus()
        );
    }
}
