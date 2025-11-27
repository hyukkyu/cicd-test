package com.example.authapp.admin;

import com.example.authapp.post.Post;
import com.example.authapp.post.PostStatus;
import com.example.authapp.post.PostModerationStatus;

import java.time.LocalDateTime;

public record PostSummaryDto(
        Long id,
        String title,
        String author,
        String mainBoardName,
        String subBoardName,
        PostStatus status,
        PostModerationStatus moderationStatus,
        boolean blocked,
        boolean harmful,
        LocalDateTime createdAt,
        LocalDateTime reportedAt
) {

    public static PostSummaryDto from(Post post) {
        return new PostSummaryDto(
                post.getId(),
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getMainBoardName(),
                post.getSubBoardName(),
                post.getStatus(),
                post.getModerationStatus(),
                post.isBlocked(),
                post.isHarmful(),
                post.getCreateDate(),
                post.getReportedAt()
        );
    }
}
