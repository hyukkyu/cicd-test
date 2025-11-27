package com.example.authapp.admin;

import com.example.authapp.comment.Comment;
import com.example.authapp.comment.CommentRepository;
import com.example.authapp.user.CognitoService;
import com.example.authapp.comment.CommentStatus;
import com.example.authapp.notice.Notice;
import com.example.authapp.notice.NoticeService;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostRepository;
import com.example.authapp.post.PostModerationStatus;
import com.example.authapp.post.PostStatus;
import com.example.authapp.report.Report;
import com.example.authapp.report.ReportRepository;
import com.example.authapp.report.ReportStatus;
import com.example.authapp.report.ReportType;
import com.example.authapp.admin.ResourceNotFoundException;
import com.example.authapp.user.User;
import com.example.authapp.user.UserRepository;
import com.example.authapp.user.UserStatus;
import com.example.authapp.video.VideoModeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminReviewItemRepository adminReviewItemRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NoticeService noticeService;
    private final CommentRepository commentRepository;
    private final DisciplinaryService disciplinaryService;
    private final ReportRepository reportRepository;
    private final CognitoService cognitoService;

    public AdminService(AdminReviewItemRepository adminReviewItemRepository,
                        PostRepository postRepository,
                        UserRepository userRepository,
                        NoticeService noticeService,
                        CommentRepository commentRepository,
                        DisciplinaryService disciplinaryService,
                        ReportRepository reportRepository,
                        CognitoService cognitoService) {
        this.adminReviewItemRepository = adminReviewItemRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.noticeService = noticeService;
        this.commentRepository = commentRepository;
        this.disciplinaryService = disciplinaryService;
        this.reportRepository = reportRepository;
        this.cognitoService = cognitoService;
    }

    public List<AdminReviewItem> getPendingReviewItems() {
        return adminReviewItemRepository.findByReviewStatusOrderByCreatedAtDesc(AdminReviewItem.ReviewStatus.PENDING);
    }

    public void approveReviewItem(Long id) {
        AdminReviewItem item = adminReviewItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found: " + id));
        restoreContent(item);
        item.setReviewStatus(AdminReviewItem.ReviewStatus.APPROVED);
        item.setReviewedAt(LocalDateTime.now());
        adminReviewItemRepository.save(item);
    }

    public void rejectReviewItem(Long id) {
        AdminReviewItem item = adminReviewItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found: " + id));
        applyContentBlock(item);
        item.setReviewStatus(AdminReviewItem.ReviewStatus.REJECTED);
        item.setReviewedAt(LocalDateTime.now());
        adminReviewItemRepository.save(item);
    }

    public void deleteReviewItem(Long id) {
        AdminReviewItem item = adminReviewItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found: " + id));
        applyContentBlock(item);
        item.setReviewStatus(AdminReviewItem.ReviewStatus.REJECTED);
        item.setReviewedAt(LocalDateTime.now());
        adminReviewItemRepository.save(item);
    }

    public AdminReviewItemDetailDto getReviewItemDetail(Long id) {
        AdminReviewItem item = getReviewItem(id);
        List<String> detectionLabels = aggregateDetectionLabels(item);
        List<AdminReviewItem> siblings = List.of();
        if (item.getPost() != null) {
            siblings = adminReviewItemRepository.findByPostAndReviewStatus(item.getPost(), AdminReviewItem.ReviewStatus.PENDING);
        } else if (item.getComment() != null) {
            siblings = adminReviewItemRepository.findByCommentAndReviewStatus(item.getComment(), AdminReviewItem.ReviewStatus.PENDING);
        }
        // include current item as well
        List<AdminReviewItem> related = new java.util.ArrayList<>(siblings);
        if (!related.contains(item)) {
            related.add(item);
        }
        return AdminReviewItemDetailDto.from(item, detectionLabels, related);
    }

    public AdminReviewItem getReviewItem(Long id) {
        return adminReviewItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review item not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getUsers() {
        return userRepository.findAll().stream()
                .map(UserSummaryDto::from)
                .collect(Collectors.toList());
    }

    public void blockUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.block();
        try {
            cognitoService.adminDisableUser(user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to disable Cognito user {} while blocking locally: {}", user.getUsername(), e.getMessage());
        }
        String message = (reason != null && !reason.isBlank())
                ? reason
                : "관리자가 커뮤니티 이용 수칙 위반으로 계정을 정지했습니다.";
        disciplinaryService.notifySuspension(user, message, "/login");
    }

    public void unblockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.unblock();
        user.setWarnCount(0L);
        userRepository.save(user);
        try {
            cognitoService.adminEnableUser(user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to enable Cognito user {} while unblocking locally: {}", user.getUsername(), e.getMessage());
        }
    }

    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setStatus(com.example.authapp.user.UserStatus.DELETED);
        user.setEnabled(false);
        user.setSuspendedAt(LocalDateTime.now());
        userRepository.save(user);
        try {
            cognitoService.adminDisableUser(user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to disable Cognito user {} while marking deleted: {}", user.getUsername(), e.getMessage());
        }
    }

    private List<String> aggregateDetectionLabels(AdminReviewItem base) {
        List<AdminReviewItem> siblings = List.of();
        if (base.getPost() != null) {
            siblings = adminReviewItemRepository.findByPostAndReviewStatus(base.getPost(), AdminReviewItem.ReviewStatus.PENDING);
        } else if (base.getComment() != null) {
            siblings = adminReviewItemRepository.findByCommentAndReviewStatus(base.getComment(), AdminReviewItem.ReviewStatus.PENDING);
        }
        return Stream.concat(Stream.of(base), siblings.stream())
                .map(AdminReviewItemDto::resolveDetectionType)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDetailDto getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        long postCount = postRepository.countByAuthorId(userId);
        long commentCount = commentRepository.countByAuthorId(userId);
        long reportCount = reportRepository.countByReporterId(userId);
        return UserDetailDto.from(user, postCount, commentCount, reportCount);
    }

    @Transactional(readOnly = true)
    public List<PostSummaryDto> getPosts(PostStatus status, Long authorId) {
        List<Post> posts;
        if (authorId != null) {
            posts = status == null
                    ? postRepository.findByAuthorId(authorId)
                    : postRepository.findByAuthorIdAndStatus(authorId, status);
        } else {
            posts = status == null ? postRepository.findAll() : postRepository.findByStatus(status);
        }
        return posts.stream().map(PostSummaryDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PostSummaryDto> getReportedPosts() {
        return postRepository.findByStatus(PostStatus.REPORTED).stream()
                .map(PostSummaryDto::from)
                .collect(Collectors.toList());
    }

    public void updatePostStatus(Long postId, PostStatus status) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (status == PostStatus.PUBLISHED) {
            post.setModerationStatus(com.example.authapp.post.PostModerationStatus.VISIBLE);
            post.setBlocked(false);
        } else if (status == PostStatus.HIDDEN) {
            post.setModerationStatus(com.example.authapp.post.PostModerationStatus.BLOCKED);
            post.setBlocked(true);
        }
        post.setStatus(status);
        postRepository.save(post);
    }

    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        User author = post.getAuthor();
        PostStatus previousStatus = post.getStatus();
        String title = post.getTitle() != null ? post.getTitle() : "제목 없음";

        post.remove();
        postRepository.save(post);

        if (author != null) {
            boolean wasPreviouslyRemoved = previousStatus == PostStatus.REMOVED;
            String message = wasPreviouslyRemoved
                    ? String.format("회원님이 삭제했던 게시글 '%s'을(를) 관리자 검수로 완전히 정리했습니다.", title)
                    : String.format("관리자가 게시글 '%s'을(를) 최종 삭제했습니다.", title);
            disciplinaryService.notifyContentAction(author, message, "/my-info");
        }
    }

    public NoticeResponseDto createNotice(NoticeRequestDto request) {
        boolean pinned = request.pinned() != null && request.pinned();
        Notice notice = noticeService.create(request.title(), request.content(), pinned, request.attachmentUrls());
        return NoticeResponseDto.from(notice);
    }

    public NoticeResponseDto updateNotice(Long noticeId, NoticeRequestDto request) {
        boolean pinned = request.pinned() != null && request.pinned();
        Notice notice = noticeService.update(noticeId, request.title(), request.content(), pinned, request.attachmentUrls());
        return NoticeResponseDto.from(notice);
    }

    public void deleteNotice(Long noticeId) {
        noticeService.delete(noticeId);
    }

    public NoticeResponseDto setNoticePinned(Long noticeId, boolean pinned) {
        Notice notice = noticeService.setPinned(noticeId, pinned);
        return NoticeResponseDto.from(notice);
    }

    @Transactional(readOnly = true)
    public List<NoticeResponseDto> getNotices() {
        return noticeService.findAll().stream()
                .map(NoticeResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NoticeResponseDto getNotice(Long id) {
        Notice notice = noticeService.getNotice(id);
        return NoticeResponseDto.from(notice);
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsDto getDashboardStats() {
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long blockedUsers = userRepository.countByStatus(UserStatus.BLOCKED);
        long publishedPosts = postRepository.countByStatus(PostStatus.PUBLISHED);
        long harmfulPosts = postRepository.countByHarmfulTrue();
        return new AdminDashboardStatsDto(activeUsers, blockedUsers, publishedPosts, harmfulPosts);
    }

    public long getTotalUserCount() {
        return userRepository.count();
    }

    public long getTotalPostCount() {
        return postRepository.count();
    }

    public long getMaliciousPostCount() {
        long blockedPosts = postRepository.countByBlocked(true);
        long rejectedReviewItems = adminReviewItemRepository.countByReviewStatus(AdminReviewItem.ReviewStatus.REJECTED);
        return blockedPosts + rejectedReviewItems;
    }

    @Transactional(readOnly = true)
    public Page<User> getUsers(Pageable pageable) {
        Pageable sorted = pageable;
        if (pageable.getSort().isUnsorted()) {
            sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return userRepository.findAll(sorted);
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    public void toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.isEnabled()) {
            user.block();
            disciplinaryService.notifySuspension(user, "관리자가 커뮤니티 이용 수칙 위반으로 계정을 정지했습니다.", "/login");
        } else {
            user.unblock();
        }
    }

    public DisciplinaryService.DisciplinaryResult warnUser(Long userId, String reason, String link) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        String message = (reason != null && !reason.isBlank())
                ? reason
                : "관리자가 경고를 부여했습니다. 커뮤니티 이용 수칙을 다시 확인해 주세요.";
        String safeLink = (link != null && !link.isBlank()) ? link : "/policy";
        return disciplinaryService.applyWarning(user, message, safeLink);
    }

    @Transactional(readOnly = true)
    public Page<Post> getPosts(Pageable pageable) {
        Pageable sorted = pageable;
        if (pageable.getSort().isUnsorted()) {
            sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createDate"));
        }
        return postRepository.findAll(sorted);
    }

    public void togglePostBlockedStatus(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        if (post.isBlocked()) {
            post.restore();
        } else {
            post.hide();
            post.setHarmful(true);
        }
    }

    public void hidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        post.hide();
        post.setHarmful(true);
    }

    public void softDeletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        post.remove();
    }

    public void restorePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        post.restore();
    }

    private void restoreContent(AdminReviewItem item) {
        if (item.getComment() != null) {
            Comment comment = commentRepository.findById(item.getComment().getId())
                    .orElse(item.getComment());
            comment.restore();
            commentRepository.save(comment);
        }
        if (item.getPost() != null) {
            Post post = postRepository.findById(item.getPost().getId())
                    .orElse(item.getPost());
            post.restore();
            post.setModerationStatus(PostModerationStatus.VISIBLE);
            postRepository.save(post);
        }
    }

    private void applyContentBlock(AdminReviewItem item) {
        User author = null;
        String link = "/";
        String message = "관리자에 의해 콘텐츠 제재가 이루어졌습니다.";

        if (item.getComment() != null) {
            Comment comment = commentRepository.findById(item.getComment().getId())
                    .orElse(item.getComment());
            comment.block();
            comment.markReported(true);
            commentRepository.save(comment);

            if (comment.getPost() != null) {
                link = "/post/detail/" + comment.getPost().getId();
            }
            author = comment.getAuthor();
            message = "작성하신 댓글이 관리자에 의해 차단되고 경고가 부여되었습니다.";
        } else if (item.getPost() != null) {
            Post post = postRepository.findById(item.getPost().getId())
                    .orElse(item.getPost());
            post.hide();
            post.setHarmful(true);
            link = "/post/detail/" + post.getId();
            postRepository.save(post);

            author = post.getAuthor();
            message = "작성하신 게시글이 관리자에 의해 차단되고 경고가 부여되었습니다.";
        }

        if (author == null) {
            log.warn("Skipping disciplinary action for review item {} because content author is missing.", item.getId());
            return;
        }

        disciplinaryService.applyWarning(author, message, link);
    }

    @Transactional(readOnly = true)
    public AdminPostDetailDto getPostDetail(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        List<Comment> commentEntities = post.getCommentList();
        List<AdminPostDetailDto.CommentView> comments = commentEntities.stream()
                .sorted(Comparator.comparing(Comment::getCreateDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(comment -> new AdminPostDetailDto.CommentView(
                        comment.getId(),
                        comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                        comment.getAuthor() != null ? comment.getAuthor().getUsername() : "알 수 없음",
                        comment.getContent(),
                        comment.getStatus() == CommentStatus.BLOCKED,
                        comment.getCreateDate()))
                .collect(Collectors.toList());

        List<AdminPostDetailDto.ImageModerationView> imageModerations = post.getImageModerations().stream()
                .map(AdminPostDetailDto.ImageModerationView::from)
                .collect(Collectors.toList());

        VideoModeration videoModeration = post.getVideoModeration();

        return new AdminPostDetailDto(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMainBoardName(),
                post.getSubBoardName(),
                post.getTabItem(),
                post.getStatus(),
                post.getModerationStatus(),
                post.isBlocked(),
                post.isHarmful(),
                post.isHarmful() && post.getModerationStatus() == com.example.authapp.post.PostModerationStatus.VISIBLE,
                post.getCreateDate(),
                post.getViewCount(),
                post.getAuthor() != null ? post.getAuthor().getId() : null,
                post.getAuthor() != null ? post.getAuthor().getUsername() : "알 수 없음",
                post.getFileUrls() != null ? List.copyOf(post.getFileUrls()) : List.of(),
                comments,
                imageModerations,
                AdminPostDetailDto.TextModerationView.from(post.getTextModeration()),
                AdminPostDetailDto.VideoModerationView.from(videoModeration)
        );
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryDto> getReports(ReportStatus status) {
        PageRequest pageRequest = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createDate"));
        List<Report> reports = (status == null)
                ? reportRepository.findAllByOrderByCreateDateDesc(pageRequest).getContent()
                : reportRepository.findByStatusOrderByCreateDateDesc(status, pageRequest).getContent();
        return reports.stream()
                .map(this::mapToReportSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReportDetailDto getReportDetail(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
        Post post = null;
        Comment comment = null;
        if (report.getType() == ReportType.POST) {
            post = postRepository.findById(report.getTargetId()).orElse(null);
        } else if (report.getType() == ReportType.COMMENT) {
            comment = commentRepository.findById(report.getTargetId()).orElse(null);
            if (comment != null) {
                post = comment.getPost();
            }
        }
        String reporterName = resolveUsername(report.getReporter());
        String reporterUsername = report.getReporter() != null ? report.getReporter().getUsername() : null;
        return ReportDetailDto.from(report, post, comment, reporterName, reporterUsername);
    }

    private ReportSummaryDto mapToReportSummary(Report report) {
        String authorName = "알 수 없음";
        String mainBoardName = null;
        String subBoardName = null;

        if (report.getType() == ReportType.POST) {
            Post post = postRepository.findById(report.getTargetId()).orElse(null);
            if (post != null) {
                authorName = resolveUsername(post.getAuthor());
                mainBoardName = post.getMainBoardName();
                subBoardName = post.getSubBoardName();
            }
        } else if (report.getType() == ReportType.COMMENT) {
            Comment comment = commentRepository.findById(report.getTargetId()).orElse(null);
            if (comment != null) {
                authorName = resolveUsername(comment.getAuthor());
                Post parent = comment.getPost();
                if (parent != null) {
                    mainBoardName = parent.getMainBoardName();
                    subBoardName = parent.getSubBoardName();
                }
            }
        }

        return ReportSummaryDto.from(
                report,
                authorName,
                mainBoardName,
                subBoardName
        );
    }

    private String resolveUsername(User user) {
        if (user == null) {
            return "알 수 없음";
        }
        String nickname = user.getNickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        return user.getUsername() != null ? user.getUsername() : "알 수 없음";
    }
}
