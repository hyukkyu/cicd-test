package com.example.authapp.comment;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public record CommentUpdateRequest(
        @NotBlank(message = "댓글 내용을 입력하세요.")
        @Size(max = 2000, message = "댓글은 2000자를 넘을 수 없습니다.")
        String content
) {
}
