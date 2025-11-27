package com.example.authapp.report;

import com.example.authapp.admin.DisciplinaryService;
import com.example.authapp.admin.ResourceNotFoundException;
import com.example.authapp.comment.Comment;
import com.example.authapp.comment.CommentRepository;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostRepository;
import com.example.authapp.notification.UserNotificationService;
import com.example.authapp.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReportAdminService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final DisciplinaryService disciplinaryService;
    private final UserNotificationService userNotificationService;
    private final com.example.authapp.user.UserRepository userRepository;

    public ReportAdminService(ReportRepository reportRepository,
                              PostRepository postRepository,
                              CommentRepository commentRepository,
                              DisciplinaryService disciplinaryService,
                              UserNotificationService userNotificationService,
                              com.example.authapp.user.UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.disciplinaryService = disciplinaryService;
        this.userNotificationService = userNotificationService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<Report> getReports(ReportStatus status, Pageable pageable) {
        if (status == null) {
            return reportRepository.findAllByOrderByCreateDateDesc(pageable);
        }
        return reportRepository.findByStatusOrderByCreateDateDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Report getReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    public void warnAndHide(Long reportId, User admin, String note) {
        Report report = ensurePending(reportId);
        ReportTarget target = loadTarget(report);

        if (target.type() == ReportType.POST && target.post() != null) {
            target.post().hide();
            target.post().setHarmful(true);
            postRepository.save(target.post());
        }
        if (target.comment() != null) {
            target.comment().block();
            target.comment().markReported(true);
            commentRepository.save(target.comment());
        }

        if (target.author() != null) {
            String message = target.type() == ReportType.POST
                    ? "신고된 게시글이 관리자에 의해 차단되고 경고가 부여되었습니다."
                    : "신고된 댓글이 관리자에 의해 차단되고 경고가 부여되었습니다.";
            message = appendAdminNote(message, note);
            disciplinaryService.applyWarning(
                    target.author(),
                    message,
                    target.link(),
                    true,
                    deriveBlockedReason(note, target)
            );
        }

        completeReport(report, admin, ReportAction.WARN_AND_HIDE, note);
        notifyReporter(report, target, "경고 및 차단", note);
        notifyAdminNote(report, target, note);
    }

    public void hideOnly(Long reportId, User admin, String note) {
        Report report = ensurePending(reportId);
        ReportTarget target = loadTarget(report);

        if (target.type() == ReportType.POST && target.post() != null) {
            target.post().hide();
            target.post().setHarmful(true);
            postRepository.save(target.post());
        }
        if (target.comment() != null) {
            target.comment().block();
            target.comment().markReported(true);
            commentRepository.save(target.comment());
        }

        if (target.author() != null) {
            String message = target.type() == ReportType.POST
                    ? "작성하신 게시글이 관리자에 의해 차단되었습니다."
                    : "작성하신 댓글이 관리자에 의해 차단되었습니다.";
            message = appendAdminNote(message, note);
            disciplinaryService.notifyContentAction(
                    target.author(),
                    message,
                    target.link(),
                    true,
                    deriveBlockedReason(note, target)
            );
        }

        completeReport(report, admin, ReportAction.HIDE_ONLY, note);
        notifyReporter(report, target, "차단", note);
        notifyAdminNote(report, target, note);
    }

    public void approve(Long reportId, User admin, String note) {
        Report report = ensurePending(reportId);
        ReportTarget target = loadTarget(report);
        User author = target.author() != null ? userRepository.findById(target.author().getId()).orElse(null) : null;

        // 승인 시에도 콘텐츠를 차단/숨김 처리하고 경고를 부여한다.
        if (target.type() == ReportType.POST && target.post() != null) {
            target.post().hide();
            target.post().setHarmful(true);
            postRepository.save(target.post());
        }
        if (target.comment() != null) {
            target.comment().block();
            target.comment().markReported(true);
            commentRepository.save(target.comment());
        }

        if (author != null) {
            String message = target.type() == ReportType.POST
                    ? "신고된 게시글이 승인되어 경고가 부여되었습니다."
                    : "신고된 댓글이 승인되어 경고가 부여되었습니다.";
            message = appendAdminNote(message, note);
            disciplinaryService.applyWarning(
                    author,
                    message,
                    target.link(),
                    true,
                    deriveBlockedReason(note, target)
            );
        }

        report.setStatus(ReportStatus.ACTION_TAKEN);
        report.setAction(ReportAction.APPROVED);
        report.setProcessedBy(admin);
        report.setProcessedAt(LocalDateTime.now());
        report.setAdminNote(note);
        reportRepository.save(report);

        notifyReporter(report, target, "승인(차단)", note);
        notifyAdminNote(report, target, note);
    }

    public void reject(Long reportId, User admin, String note) {
        Report report = ensurePending(reportId);
        ReportTarget target = loadTarget(report);

        restoreTarget(target);

        if (target.author() != null) {
            String message = target.type() == ReportType.POST
                    ? "신고가 반려되어 게시글이 복구되었습니다."
                    : "신고가 반려되어 댓글이 복구되었습니다.";
            message = appendAdminNote(message, note);
            disciplinaryService.notifyContentAction(target.author(), message, target.link());
        }

        report.setStatus(ReportStatus.REJECTED);
        report.setAction(ReportAction.REJECTED);
        report.setProcessedBy(admin);
        report.setProcessedAt(LocalDateTime.now());
        report.setAdminNote(note);
        reportRepository.save(report);

        notifyReporter(report, target, "반려", note);
        notifyAdminNote(report, target, note);
    }

    private Report ensurePending(Long reportId) {
        Report report = getReport(reportId);
        if (report.getStatus() != ReportStatus.PENDING) {
            throw new IllegalStateException("Report is already processed.");
        }
        return report;
    }

    private void completeReport(Report report, User admin, ReportAction action, String note) {
        report.setStatus(ReportStatus.ACTION_TAKEN);
        report.setAction(action);
        report.setProcessedBy(admin);
        report.setProcessedAt(LocalDateTime.now());
        report.setAdminNote(note);
        reportRepository.save(report);
    }

    private ReportTarget loadTarget(Report report) {
        if (report.getType() == ReportType.POST) {
            Post post = postRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + report.getTargetId()));
            User author = post.getAuthor() != null ? userRepository.findById(post.getAuthor().getId()).orElse(null) : null;
            return new ReportTarget(report.getType(), post, null, author, "/post/detail/" + post.getId());
        } else if (report.getType() == ReportType.COMMENT) {
            Comment comment = commentRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + report.getTargetId()));
            Post parentPost = comment.getPost();
            String link = parentPost != null ? "/post/detail/" + parentPost.getId() : "/";
            User author = comment.getAuthor() != null ? userRepository.findById(comment.getAuthor().getId()).orElse(null) : null;
            return new ReportTarget(report.getType(), parentPost, comment, author, link);
        } else {
            throw new UnsupportedOperationException("Unsupported report type: " + report.getType());
        }
    }

    private void restoreTarget(ReportTarget target) {
        if (target.comment() != null) {
            target.comment().restore();
            commentRepository.save(target.comment());
        }
        if (target.type() == ReportType.POST && target.post() != null) {
            target.post().restore();
            target.post().setHarmful(false);
            postRepository.save(target.post());
        }
    }

    private void notifyReporter(Report report, ReportTarget target, String actionLabel, String note) {
        User reporter = report.getReporter();
        if (reporter == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("신고하신 ").append(buildTargetLabel(target))
                .append("에 대한 조치가 완료되었습니다. 결과: ").append(actionLabel).append(".");
        if (note != null && !note.isBlank()) {
            builder.append(" 관리자 메모: ").append(note.trim());
        }
        userNotificationService.notifyReportResult(reporter, builder.toString(), target.link());
    }

    private void notifyAdminNote(Report report, ReportTarget target, String note) {
        if (!StringUtils.hasText(note)) {
            return;
        }
        String sanitizedNote = note.trim();
        String shortNote = abbreviate(sanitizedNote, 200);
        if (target.author() != null) {
            String targetMessage = "관리자가 귀하의 " + buildTargetLabel(target) + "에 다음 메모를 남겼습니다: " + shortNote;
            userNotificationService.notifyAdminNote(target.author(), targetMessage, target.link());
        }
        User reporter = report.getReporter();
        if (reporter != null) {
            String reporterMessage = "관리자가 신고하신 " + buildTargetLabel(target) + "에 다음 메모를 남겼습니다: " + shortNote;
            userNotificationService.notifyAdminNote(reporter, reporterMessage, target.link());
        }
    }

    private String deriveBlockedReason(String note, ReportTarget target) {
        if (note != null && !note.isBlank()) {
            return note.trim();
        }
        return target.type() == ReportType.POST
                ? "관리자에 의해 게시글이 차단되었습니다."
                : "관리자에 의해 댓글이 차단되었습니다.";
    }

    private String buildTargetLabel(ReportTarget target) {
        if (target.type() == ReportType.POST) {
            Long id = target.post() != null ? target.post().getId() : target.comment() != null && target.comment().getPost() != null ? target.comment().getPost().getId() : null;
            String title = target.post() != null ? target.post().getTitle() : null;
            return "게시글" + (id != null ? " #" + id : "") + (title != null && !title.isBlank() ? " '" + abbreviate(title, 40) + "'" : "");
        }
        Long id = target.comment() != null ? target.comment().getId() : null;
        String content = target.comment() != null ? target.comment().getContent() : null;
        return "댓글" + (id != null ? " #" + id : "") + (content != null && !content.isBlank() ? " '" + abbreviate(content, 40) + "'" : "");
    }

    private String appendAdminNote(String message, String note) {
        if (note == null || note.isBlank()) {
            return message;
        }
        return message + " 관리자 메모: " + note.trim();
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, max - 3)) + "...";
    }

    private record ReportTarget(
            ReportType type,
            Post post,
            Comment comment,
            User author,
            String link
    ) {
    }
}
