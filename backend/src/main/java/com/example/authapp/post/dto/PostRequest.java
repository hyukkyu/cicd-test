package com.example.authapp.post.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Collections;
import java.util.List;

public record PostRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotBlank String mainBoardName,
        @NotBlank String subBoardName,
        @Size(max = 50) String tabItem,
        List<String> fileUrls
) {
    public List<String> fileUrls() {
        return fileUrls == null ? Collections.emptyList() : List.copyOf(fileUrls);
    }
}
