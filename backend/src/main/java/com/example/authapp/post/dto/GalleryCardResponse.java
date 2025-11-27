package com.example.authapp.post.dto;

import com.example.authapp.post.Post;
import com.example.authapp.user.User;

import java.time.LocalDateTime;
import java.util.List;

public record GalleryCardResponse(
        Long id,
        String title,
        String authorUsername,
        String authorNickname,
        String mainBoardName,
        String subBoardName,
        LocalDateTime createdAt,
        int likeCount,
        long commentCount,
        List<String> thumbnailUrls
) {

    public static GalleryCardResponse from(Post post, long commentCount) {
        return new GalleryCardResponse(
                post.getId(),
                post.getTitle(),
                username(post.getAuthor()),
                nickname(post.getAuthor()),
                post.getMainBoardName(),
                post.getSubBoardName(),
                post.getCreateDate(),
                post.getVoter() != null ? post.getVoter().size() : 0,
                commentCount,
                buildThumbnails(post.getFileUrls())
        );
    }

    private static List<String> buildThumbnails(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return List.of();
        }
        return fileUrls.size() <= 3 ? List.copyOf(fileUrls) : fileUrls.subList(0, 3);
    }

    private static String username(User user) {
        return user != null ? user.getUsername() : null;
    }

    private static String nickname(User user) {
        return user != null ? user.getNickname() : null;
    }
}
