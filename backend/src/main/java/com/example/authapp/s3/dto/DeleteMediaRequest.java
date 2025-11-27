package com.example.authapp.s3.dto;

import javax.validation.constraints.NotBlank;

public record DeleteMediaRequest(
        @NotBlank
        String key
) {
}
