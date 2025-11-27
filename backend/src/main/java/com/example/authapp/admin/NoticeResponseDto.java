package com.example.authapp.admin;

import com.example.authapp.notice.Notice;

import java.time.LocalDateTime;
import java.util.List;

public record NoticeResponseDto(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean pinned,
        List<String> attachmentUrls
) {

    public static NoticeResponseDto from(Notice notice) {
        return new NoticeResponseDto(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreateDate(),
                notice.getUpdateDate(),
                notice.isPinned(),
                notice.getAttachmentUrls()
        );
    }
}
