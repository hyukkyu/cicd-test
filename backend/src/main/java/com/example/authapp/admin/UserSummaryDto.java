package com.example.authapp.admin;

import com.example.authapp.user.User;
import com.example.authapp.user.UserStatus;

import java.time.LocalDateTime;

public record UserSummaryDto(
        Long id,
        String username,
        String email,
        String nickname,
        UserStatus status,
        boolean enabled,
        LocalDateTime createdAt
) {

    public static UserSummaryDto from(User user) {
        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getStatus(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
