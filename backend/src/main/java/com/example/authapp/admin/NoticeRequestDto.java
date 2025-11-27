package com.example.authapp.admin;

import javax.validation.constraints.NotBlank;
import java.util.List;

public record NoticeRequestDto(
        @NotBlank String title,
        @NotBlank String content,
        Boolean pinned,
        List<String> attachmentUrls
) {
}
