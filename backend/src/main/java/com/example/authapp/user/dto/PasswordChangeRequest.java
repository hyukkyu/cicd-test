package com.example.authapp.user.dto;

import javax.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력하세요.") String currentPassword,
        @NotBlank(message = "새 비밀번호를 입력하세요.") String newPassword,
        @NotBlank(message = "새 비밀번호 확인을 입력하세요.") String confirmNewPassword
) {
}
