package com.example.authapp.post.dto;

import com.example.authapp.post.Post;
import com.example.authapp.post.PostModerationStatus;
import com.example.authapp.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record PostSummaryResponse(
        Long id,
        String title,
        String excerpt,
        String authorUsername,
        String authorNickname,
        String mainBoardName,
        String subBoardName,
        String tabItem,
        LocalDateTime createdAt,
        int viewCount,
        int voteCount,
        boolean blocked,
        boolean harmful,
        PostModerationStatus moderationStatus,
        List<String> fileUrls
) {

    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                buildExcerpt(post.getContent()),
                username(post.getAuthor()),
                nickname(post.getAuthor()),
                post.getMainBoardName(),
                post.getSubBoardName(),
                post.getTabItem(),
                post.getCreateDate(),
                post.getViewCount(),
                post.getVoter() != null ? post.getVoter().size() : 0,
                post.isBlocked(),
                post.isHarmful(),
                post.getModerationStatus(),
                List.copyOf(post.getFileUrls())
        );
    }

    private static String username(User user) {
        return user != null ? user.getUsername() : null;
    }

    private static String nickname(User user) {
        return user != null ? user.getNickname() : null;
    }

    private static String buildExcerpt(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 117) + "..." : normalized;
    }
}
