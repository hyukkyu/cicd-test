package com.example.authapp.post.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

public record GalleryWriteRequest(
        @NotBlank(message = "제목을 입력하세요.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @NotBlank(message = "본문을 입력하세요.")
        String content,

        @NotBlank(message = "메인 보드를 선택하세요.")
        String mainBoardName,

        @NotBlank(message = "서브 보드를 선택하세요.")
        String subBoardName,

        @Size(max = 50, message = "탭 이름은 50자를 넘을 수 없습니다.")
        String tabItem,

        @NotEmpty(message = "최소 한 개 이상의 첨부 파일을 등록하세요.")
        List<@NotBlank String> attachmentUrls
) {
}
