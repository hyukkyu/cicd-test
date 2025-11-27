package com.example.authapp.user.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

public record FindIdRequest(
        @NotBlank @Email String email
) {
}
