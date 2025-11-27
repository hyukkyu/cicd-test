package com.example.authapp.user.dto;

import com.example.authapp.user.User;

import java.time.LocalDateTime;

public record ProfileResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String role,
        String status,
        boolean enabled,
        Long warnCount,
        LocalDateTime createdAt,
        String profilePictureUrl
) {
    public static ProfileResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.isEnabled(),
                user.getWarnCount(),
                user.getCreatedAt(),
                user.getProfilePictureUrl()
        );
    }
}
