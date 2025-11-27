package com.example.authapp.s3.dto;

import javax.validation.constraints.NotBlank;

public record PresignedUploadRequest(
        @NotBlank(message = "업로드 디렉터리를 입력하세요.")
        String directory,

        String contentType
) {
}
