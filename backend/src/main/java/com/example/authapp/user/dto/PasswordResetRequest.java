package com.example.authapp.user.dto;

import javax.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank String username,
        @NotBlank String verificationCode,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {
}
