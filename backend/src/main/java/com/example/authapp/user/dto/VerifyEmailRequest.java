package com.example.authapp.user.dto;

import javax.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank String username,
        @NotBlank String code
) {
}
