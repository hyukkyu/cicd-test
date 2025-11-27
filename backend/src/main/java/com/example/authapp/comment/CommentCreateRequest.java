package com.example.authapp.comment;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public record CommentCreateRequest(
        @NotNull(message = "postId는 필수입니다.")
        Long postId,

        Long parentId,

        @NotBlank(message = "댓글 내용을 입력하세요.")
        @Size(max = 2000, message = "댓글은 2000자를 넘을 수 없습니다.")
        String content
) {
}
