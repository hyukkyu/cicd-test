package com.example.authapp.report;

import com.example.authapp.admin.NotificationService;
import com.example.authapp.comment.CommentRepository;
import com.example.authapp.notification.UserNotificationService;
import com.example.authapp.post.PostRepository;
import com.example.authapp.user.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;
    private final CommentRepository commentRepository;
    private final UserNotificationService userNotificationService;

    public ReportService(ReportRepository reportRepository,
                         PostRepository postRepository,
                         NotificationService notificationService,
                         CommentRepository commentRepository,
                         UserNotificationService userNotificationService) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
        this.commentRepository = commentRepository;
        this.userNotificationService = userNotificationService;
    }

    public boolean create(Long targetId, ReportType type, String reason, User reporter) {
        if (reportRepository.existsByReporterIdAndTargetIdAndType(reporter.getId(), targetId, type)) {
            return false; // Already reported
        }

        Report report = new Report();
        report.setTargetId(targetId);
        report.setType(type);
        report.setReason(reason);
        report.setReporter(reporter);
        report.setCreateDate(LocalDateTime.now());
        report.setStatus(ReportStatus.PENDING);
        Report saved = reportRepository.save(report);

        if (type == ReportType.POST) {
            postRepository.findById(targetId)
                    .ifPresent(post -> post.markReported(false));
        } else if (type == ReportType.COMMENT) {
            commentRepository.findById(targetId)
                    .ifPresent(comment -> comment.markReported(true));
        }

        notificationService.notifyReport(saved);
        userNotificationService.notifyReportSubmitted(
                reporter,
                "신고가 정상적으로 접수되었습니다. 관리자 검토 후 결과를 안내해 드리겠습니다.",
                "/my-info"
        );
        return true;
    }
}
