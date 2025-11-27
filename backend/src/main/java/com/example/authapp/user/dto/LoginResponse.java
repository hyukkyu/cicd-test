package com.example.authapp.user.dto;

import com.example.authapp.user.User;

public record LoginResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String role,
        String profilePictureUrl
) {
    public static LoginResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getProfilePictureUrl()
        );
    }
}
