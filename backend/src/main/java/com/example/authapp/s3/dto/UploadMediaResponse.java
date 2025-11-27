package com.example.authapp.s3.dto;

public record UploadMediaResponse(
        String url,
        String key,
        String bucket,
        String contentType,
        long size
) {

    public static UploadMediaResponse from(com.example.authapp.s3.S3Service.UploadResult result) {
        return new UploadMediaResponse(
                result.publicUrl(),
                result.key(),
                result.bucket(),
                result.contentType(),
                result.size()
        );
    }
}
