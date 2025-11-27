package com.example.authapp.admin;

import java.time.Instant;

public record S3ObjectDto(
        String key,
        long size,
        Instant lastModified
) {
}
