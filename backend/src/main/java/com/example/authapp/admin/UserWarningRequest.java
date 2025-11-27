package com.example.authapp.admin;

import javax.validation.constraints.Size;

public record UserWarningRequest(
        @Size(max = 200) String message,
        @Size(max = 200) String link
) {
    public String safeMessage() {
        return (message == null || message.isBlank())
                ? "커뮤니티 이용 수칙 위반으로 경고가 발송되었습니다."
                : message;
    }

    public String safeLink() {
        return (link == null || link.isBlank()) ? "/policy" : link;
    }
}
