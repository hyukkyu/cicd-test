package com.example.authapp.post.dto;

import com.example.authapp.post.Post;
import com.example.authapp.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record GalleryDetailResponse(
        Long id,
        String title,
        String content,
        String authorUsername,
        String authorNickname,
        String mainBoardName,
        String subBoardName,
        LocalDateTime createdAt,
        int viewCount,
        int likeCount,
        long commentCount,
        List<String> attachments,
        List<CommentResponse> comments,
        boolean viewerHasLiked
) {

    public static GalleryDetailResponse from(Post post,
                                             List<CommentResponse> comments,
                                             long commentCount,
                                             boolean viewerHasLiked) {
        return new GalleryDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                username(post.getAuthor()),
                nickname(post.getAuthor()),
                post.getMainBoardName(),
                post.getSubBoardName(),
                post.getCreateDate(),
                post.getViewCount(),
                post.getVoter() != null ? post.getVoter().size() : 0,
                commentCount,
                List.copyOf(post.getFileUrls()),
                comments,
                viewerHasLiked
        );
    }

    private static String username(User user) {
        return user != null ? user.getUsername() : null;
    }

    private static String nickname(User user) {
        return user != null ? user.getNickname() : null;
    }
}
