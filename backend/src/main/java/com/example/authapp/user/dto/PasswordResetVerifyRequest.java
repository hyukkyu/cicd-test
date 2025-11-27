package com.example.authapp.user.dto;

import javax.validation.constraints.NotBlank;

public record PasswordResetVerifyRequest(
        @NotBlank String username,
        @NotBlank String code
) {
}
