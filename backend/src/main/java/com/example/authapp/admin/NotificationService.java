package com.example.authapp.admin;

import com.example.authapp.comment.Comment;
import com.example.authapp.comment.CommentRepository;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostRepository;
import com.example.authapp.report.Report;
import com.example.authapp.report.ReportRepository;
import com.example.authapp.report.ReportType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class NotificationService {

    private static final String DESTINATION = "/topic/admin/notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final AdminReviewItemRepository adminReviewItemRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               SimpMessagingTemplate messagingTemplate,
                               PostRepository postRepository,
                               CommentRepository commentRepository,
                               ReportRepository reportRepository,
                               AdminReviewItemRepository adminReviewItemRepository) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.adminReviewItemRepository = adminReviewItemRepository;
    }

    @Transactional
    public NotificationDto notifyAdmin(AdminReviewItem item) {
        NotificationContext context = buildContextForReviewItem(item);

        // Deduplicate: if a pending content warning for the same post/comment exists, merge labels.
        NotificationDto merged = tryMergeContentWarning(item, context);
        if (merged != null) {
            return merged;
        }

        Notification notification = new Notification();
        notification.setType(NotificationType.CONTENT_WARNING);
        notification.setMessage(context.message());
        notification.setTargetId(item.getId());
        notification.setRead(false);

        return dispatch(notification, context);
    }

    @Transactional
    public NotificationDto notifyReport(Report report) {
        NotificationContext context = buildContextForReport(report);

        Notification notification = new Notification();
        notification.setType(NotificationType.REPORT);
        notification.setMessage(context.message());
        notification.setTargetId(report.getId());
        notification.setRead(false);

        return dispatch(notification, context);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return notificationRepository.findAllByOrderByCreatedAtDesc(
                        PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toDto)
                .getContent();
    }

    @Transactional(readOnly = true)
    public long countUnreadNotifications() {
        return notificationRepository.countByReadFalse();
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (!notification.isRead()) {
                notification.markRead();
                notificationRepository.save(notification);
                messagingTemplate.convertAndSend(DESTINATION, toDto(notification));
            }
        });
    }

    @Transactional
    public void markAllAsRead() {
        List<Notification> unread = notificationRepository.findByReadFalseOrderByCreatedAtDesc();
        if (unread.isEmpty()) {
            return;
        }

        unread.forEach(Notification::markRead);
        notificationRepository.saveAll(unread);

        List<NotificationDto> payload = unread.stream().map(this::toDto).collect(Collectors.toList());
        messagingTemplate.convertAndSend(DESTINATION, payload);
    }

    private NotificationDto dispatch(Notification notification, NotificationContext context) {
        Notification saved = notificationRepository.save(notification);
        NotificationContext resolved = context != null ? context : resolveContext(saved);
        NotificationDto dto = toDto(saved, resolved);
        messagingTemplate.convertAndSend(DESTINATION, dto);
        return dto;
    }

    private NotificationDto dispatch(Notification notification) {
        return dispatch(notification, null);
    }

    private NotificationDto toDto(Notification notification) {
        NotificationContext context = resolveContext(notification);
        return toDto(notification, context);
    }

    private NotificationDto toDto(Notification notification, NotificationContext context) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt(),
                resolveLink(notification),
                context.targetLabel(),
                context.authorLabel(),
                context.boardLabel(),
                context.summary(),
                context.mainBoardName(),
                context.subBoardName(),
                context.reporterLabel(),
                context.detectionLabel()
        );
    }

    private String resolveLink(Notification notification) {
        return switch (notification.getType()) {
            case REPORT -> "/admin/reports/" + notification.getTargetId();
            case CONTENT_WARNING -> "/admin/moderation-queue/detail/" + notification.getTargetId();
        };
    }

    private NotificationContext resolveContext(Notification notification) {
        if (notification.getType() == NotificationType.CONTENT_WARNING) {
            AdminReviewItem item = adminReviewItemRepository.findById(notification.getTargetId()).orElse(null);
            if (item != null) {
                NotificationContext context = buildContextForReviewItem(item);
                return context.withMessage(notification.getMessage());
            }
        } else if (notification.getType() == NotificationType.REPORT) {
            Report report = reportRepository.findById(notification.getTargetId()).orElse(null);
            if (report != null) {
                NotificationContext context = buildContextForReport(report);
                return context.withMessage(notification.getMessage());
            }
        }
        return NotificationContext.fallback(notification.getMessage());
    }

    private NotificationDto tryMergeContentWarning(AdminReviewItem item, NotificationContext newContext) {
        // Only attempt merge when we can resolve a stable target (post or comment).
        Long postId = item.getPost() != null ? item.getPost().getId() : null;
        Long commentId = item.getComment() != null ? item.getComment().getId() : null;
        if (postId == null && commentId == null) {
            return null;
        }

        List<Notification> unreadWarnings = notificationRepository
                .findByTypeAndReadFalseOrderByCreatedAtDesc(NotificationType.CONTENT_WARNING);
        for (Notification existing : unreadWarnings) {
            NotificationContext existingContext = resolveContext(existing);
            AdminReviewItem existingItem = adminReviewItemRepository.findById(existing.getTargetId()).orElse(null);
            if (existingItem == null) continue;
            Long existingPostId = existingItem.getPost() != null ? existingItem.getPost().getId() : null;
            Long existingCommentId = existingItem.getComment() != null ? existingItem.getComment().getId() : null;

            boolean samePost = postId != null && postId.equals(existingPostId);
            boolean sameComment = commentId != null && commentId.equals(existingCommentId);
            if (!samePost && !sameComment) {
                continue;
            }

            // Merge detection labels using all pending review items for the same target.
            String mergedLabel = aggregateDetectionLabels(existingItem);
            NotificationContext mergedContext = existingContext.withDetectionLabel(mergedLabel);
            NotificationDto dto = toDto(existing, mergedContext);
            messagingTemplate.convertAndSend(DESTINATION, dto);
            return dto;
        }
        return null;
    }

    private NotificationContext buildContextForReviewItem(AdminReviewItem item) {
        String contentType = Optional.ofNullable(item.getContentType()).orElse("콘텐츠");
        Post post = item.getPost();
        Comment comment = item.getComment();

        if (post != null) {
            String targetLabel = String.format("유해감지 - 게시글 #%d", post.getId());
            String authorLabel = resolveAuthor(post.getAuthor());
            String boardLabel = formatBoardLabel(post);
            String summary = abbreviate(Optional.ofNullable(post.getTitle()).orElse("(제목 없음)"), 60);
            String message = String.format("유해 콘텐츠 검토 요청 (%s)", contentType);
            return enhanceDetectionLabel(item, new NotificationContext(
                    message,
                    targetLabel,
                    authorLabel,
                    boardLabel,
                    summary,
                    post.getMainBoardName(),
                    post.getSubBoardName(),
                    null,
                    resolveDetectionLabel(item)
            ));
        }

        if (comment != null) {
            Post parent = comment.getPost();
            String targetLabel = String.format("유해감지 - 댓글 #%d", comment.getId());
            String authorLabel = resolveAuthor(comment.getAuthor());
            String boardLabel = parent != null ? formatBoardLabel(parent) : "게시판 미확인";
            String summary = abbreviate(Optional.ofNullable(comment.getContent()).orElse("(내용 없음)"), 60);
            String message = String.format("유해 콘텐츠 검토 요청 (%s)", contentType);
            return enhanceDetectionLabel(item, new NotificationContext(
                    message,
                    targetLabel,
                    authorLabel,
                    boardLabel,
                    summary,
                    parent != null ? parent.getMainBoardName() : null,
                    parent != null ? parent.getSubBoardName() : null,
                    null,
                    resolveDetectionLabel(item)
            ));
        }

        String message = String.format("모더레이션 요청 [%s]", contentType);
        return enhanceDetectionLabel(item, NotificationContext.fallback(message).withDetectionLabel(resolveDetectionLabel(item)));
    }

    private NotificationContext buildContextForReport(Report report) {
        String typeLabel = report.getType() == ReportType.POST ? "게시글" : "댓글";
        String reporterLabel = resolveAuthor(report.getReporter());
        String boardLabel = "게시판 미확인";
        String authorLabel = "알 수 없음";
        String summary = "-";
        String mainBoardName = null;
        String subBoardName = null;

        if (report.getType() == ReportType.POST) {
            Post post = fetchPost(report.getTargetId());
            if (post != null) {
                boardLabel = formatBoardLabel(post);
                authorLabel = resolveAuthor(post.getAuthor());
                summary = abbreviate(Optional.ofNullable(post.getTitle()).orElse("(제목 없음)"), 60);
                mainBoardName = post.getMainBoardName();
                subBoardName = post.getSubBoardName();
            }
        } else if (report.getType() == ReportType.COMMENT) {
            Comment comment = fetchComment(report.getTargetId());
            if (comment != null) {
                authorLabel = resolveAuthor(comment.getAuthor());
                summary = abbreviate(Optional.ofNullable(comment.getContent()).orElse("(내용 없음)"), 60);
                Post parent = comment.getPost();
                if (parent != null) {
                    boardLabel = formatBoardLabel(parent);
                    mainBoardName = parent.getMainBoardName();
                    subBoardName = parent.getSubBoardName();
                }
            }
        }

        String targetLabel = String.format("신고 - %s #%d", typeLabel, report.getTargetId());
        String message = String.format("신고 접수 알림: %s #%d", typeLabel, report.getTargetId());
        if (reporterLabel != null && !reporterLabel.isBlank()) {
            message += " (신고자: " + reporterLabel + ")";
        }
        return new NotificationContext(
                message,
                targetLabel,
                authorLabel,
                boardLabel,
                summary,
                mainBoardName,
                subBoardName,
                reporterLabel,
                null
        );
    }

    private NotificationContext enhanceDetectionLabel(AdminReviewItem base, NotificationContext context) {
        String merged = aggregateDetectionLabels(base);
        if (merged == null || merged.isBlank()) {
            return context;
        }
        return context.withDetectionLabel(merged);
    }

    private String aggregateDetectionLabels(AdminReviewItem base) {
        Set<String> labels = new LinkedHashSet<>();
        String self = resolveDetectionLabel(base);
        if (self != null && !self.isBlank()) {
            labels.add(self);
        }
        List<AdminReviewItem> siblings = List.of();
        if (base.getPost() != null) {
            siblings = adminReviewItemRepository.findByPostAndReviewStatus(base.getPost(), AdminReviewItem.ReviewStatus.PENDING);
        } else if (base.getComment() != null) {
            siblings = adminReviewItemRepository.findByCommentAndReviewStatus(base.getComment(), AdminReviewItem.ReviewStatus.PENDING);
        }
        for (AdminReviewItem item : siblings) {
            String label = resolveDetectionLabel(item);
            if (label != null && !label.isBlank()) {
                labels.add(label);
            }
        }
        if (labels.isEmpty()) {
            return null;
        }
        return String.join(", ", labels);
    }

    private String formatBoardLabel(Post post) {
        if (post == null) {
            return "게시판 미확인";
        }
        String main = translateBoardName(post.getMainBoardName());
        String sub = Optional.ofNullable(post.getSubBoardName()).filter(s -> !s.isBlank()).orElse("");
        return sub.isBlank() ? main : main + " / " + sub;
    }

    private String resolveAuthor(com.example.authapp.user.User user) {
        if (user == null) {
            return "알 수 없음";
        }
        String nickname = user.getNickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return user.getUsername() != null ? user.getUsername() : "알 수 없음";
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

    private Post fetchPost(Long postId) {
        if (postId == null) {
            return null;
        }
        return postRepository.findById(postId).orElse(null);
    }

    private Comment fetchComment(Long commentId) {
        if (commentId == null) {
            return null;
        }
        return commentRepository.findById(commentId).orElse(null);
    }

    private String translateBoardName(String mainBoardName) {
        if (mainBoardName == null || mainBoardName.isBlank()) {
            return "자유게시판";
        }
        String normalized = mainBoardName.trim().toLowerCase();
        return BOARD_DISPLAY_NAMES.getOrDefault(normalized, mainBoardName);
    }

    private static final Map<String, String> BOARD_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("game", "게임"),
            Map.entry("travel", "여행"),
            Map.entry("exercise", "운동"),
            Map.entry("movie", "영화"),
            Map.entry("music", "음악"),
            Map.entry("invest", "투자"),
            Map.entry("freeboard", "자유게시판"),
            Map.entry("free", "자유게시판"),
            Map.entry("gallery", "갤러리"),
            Map.entry("notice", "공지사항")
    );

    private record NotificationContext(
            String message,
            String targetLabel,
            String authorLabel,
            String boardLabel,
            String summary,
            String mainBoardName,
            String subBoardName,
            String reporterLabel,
            String detectionLabel
    ) {
        static NotificationContext fallback(String message) {
            return new NotificationContext(
                    message,
                    "콘텐츠",
                    "알 수 없음",
                    "게시판 미확인",
                    "",
                    null,
                    null,
                    null,
                    null
            );
        }

        NotificationContext withMessage(String message) {
            return new NotificationContext(
                    message,
                    this.targetLabel,
                    this.authorLabel,
                    this.boardLabel,
                    this.summary,
                    this.mainBoardName,
                    this.subBoardName,
                    this.reporterLabel,
                    this.detectionLabel
            );
        }

        NotificationContext withDetectionLabel(String detectionLabel) {
            return new NotificationContext(
                    this.message,
                    this.targetLabel,
                    this.authorLabel,
                    this.boardLabel,
                    this.summary,
                    this.mainBoardName,
                    this.subBoardName,
                    this.reporterLabel,
                    detectionLabel
            );
        }
    }

    private String resolveDetectionLabel(AdminReviewItem item) {
        String label = AdminReviewItemDto.resolveDetectionType(item);
        return (label != null && !label.isBlank()) ? label : null;
    }
}
