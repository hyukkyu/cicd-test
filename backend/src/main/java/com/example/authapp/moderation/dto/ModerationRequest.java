package com.example.authapp.moderation.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record ModerationRequest(
        @NotBlank
        @Size(max = 150)
        String title,
        @NotBlank
        @Size(max = 5000)
        String body,
        @NotBlank
        String authorId,
        @Size(max = 50)
        String board
) {
}
