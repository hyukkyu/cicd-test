package com.example.authapp.report;

public record ReportResponse(
        String message,
        ReportStatus status
) {
    public static ReportResponse success() {
        return new ReportResponse("신고가 접수되었습니다.", ReportStatus.PENDING);
    }

    public static ReportResponse duplicate() {
        return new ReportResponse("이미 신고한 콘텐츠입니다.", ReportStatus.PENDING);
    }
}
