package com.example.authapp.post.dto;

import com.example.authapp.post.Post;
import com.example.authapp.post.PostModerationStatus;
import com.example.authapp.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String title,
        String content,
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
        List<String> fileUrls,
        List<CommentResponse> comments
) {

    public static PostDetailResponse from(Post post, List<CommentResponse> comments) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
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
                List.copyOf(post.getFileUrls()),
                comments
        );
    }

    private static String username(User user) {
        return user != null ? user.getUsername() : null;
    }

    private static String nickname(User user) {
        return user != null ? user.getNickname() : null;
    }
}
