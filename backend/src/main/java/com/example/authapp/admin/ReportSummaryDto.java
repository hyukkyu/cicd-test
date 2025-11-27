package com.example.authapp.admin;

import com.example.authapp.report.Report;
import com.example.authapp.report.ReportStatus;
import com.example.authapp.report.ReportType;

import java.time.LocalDateTime;
import java.util.Optional;

public record ReportSummaryDto(
        Long id,
        ReportType type,
        ReportStatus status,
        Long targetId,
        String reporterName,
        String authorName,
        String mainBoardName,
        String subBoardName,
        String reason,
        LocalDateTime createdAt
) {

    public static ReportSummaryDto from(Report report, String authorName, String mainBoard, String subBoard) {
        return new ReportSummaryDto(
                report.getId(),
                report.getType(),
                report.getStatus(),
                report.getTargetId(),
                resolveUser(report),
                authorName,
                mainBoard,
                subBoard,
                Optional.ofNullable(report.getReason()).orElse("-"),
                report.getCreateDate()
        );
    }

    private static String resolveUser(Report report) {
        if (report.getReporter() == null) {
            return "알 수 없음";
        }
        String nickname = report.getReporter().getNickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return report.getReporter().getUsername();
    }
}
