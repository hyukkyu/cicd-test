package com.example.authapp.report;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public record ReportRequest(
        @NotNull(message = "targetId는 필수입니다.")
        Long targetId,

        @NotNull(message = "신고 대상을 선택하세요.")
        ReportType type,

        @NotBlank(message = "신고 사유를 입력하세요.")
        @Size(max = 500, message = "신고 사유는 500자를 넘을 수 없습니다.")
        String reason
) {
}
