package com.example.authapp.comment;

import javax.validation.constraints.NotEmpty;

public class CommentForm {
    @NotEmpty(message = "내용은 필수항목입니다.")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
