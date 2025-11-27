package com.example.authapp.user.dto;

import javax.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank String username
) {
}
