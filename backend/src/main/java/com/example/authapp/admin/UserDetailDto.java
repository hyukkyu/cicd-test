package com.example.authapp.admin;

import com.example.authapp.user.User;
import com.example.authapp.user.UserStatus;

import java.time.LocalDateTime;

public record UserDetailDto(
        Long id,
        String username,
        String nickname,
        String email,
        UserStatus status,
        boolean enabled,
        Long warnCount,
        LocalDateTime createdAt,
        LocalDateTime lastSanctionAt,
        LocalDateTime suspendedAt,
        String profilePictureUrl,
        long postCount,
        long commentCount,
        long reportCount
) {
    public static UserDetailDto from(User user,
                                     long postCount,
                                     long commentCount,
                                     long reportCount) {
        return new UserDetailDto(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getStatus(),
                user.isEnabled(),
                user.getWarnCount(),
                user.getCreatedAt(),
                user.getLastSanctionAt(),
                user.getSuspendedAt(),
                user.getProfilePictureUrl(),
                postCount,
                commentCount,
                reportCount
        );
    }
}
