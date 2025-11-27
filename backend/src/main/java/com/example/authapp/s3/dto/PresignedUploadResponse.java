package com.example.authapp.s3.dto;

import java.time.Instant;

public record PresignedUploadResponse(
        String uploadUrl,
        String publicUrl,
        String key,
        Instant expiresAt
) {
    public static PresignedUploadResponse from(com.example.authapp.s3.S3Service.PresignedUpload presigned) {
        return new PresignedUploadResponse(
                presigned.uploadUrl(),
                presigned.publicUrl(),
                presigned.key(),
                presigned.expiresAt()
        );
    }
}
