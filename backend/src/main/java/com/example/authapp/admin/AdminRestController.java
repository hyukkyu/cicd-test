package com.example.authapp.admin;

import com.example.authapp.post.PostStatus;
import com.example.authapp.report.ReportStatus;
import com.example.authapp.report.ReportAdminService;
import com.example.authapp.service.AdminDashboardService;
import com.example.authapp.service.dto.AdminDashboardData;
import com.example.authapp.admin.ReportDetailDto;
import com.example.authapp.s3.S3Service;
import com.example.authapp.s3.dto.PresignedUploadRequest;
import com.example.authapp.s3.dto.PresignedUploadResponse;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import java.util.List;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRestController {

    private static final Logger log = LoggerFactory.getLogger(AdminRestController.class);

    private final AdminService adminService;
    private final AdminDashboardService adminDashboardService;
    private final MonitoringService monitoringService;
    private final AwsService awsService;
    private final ReportAdminService reportAdminService;
    private final UserService userService;
    private final S3Service s3Service;

    public AdminRestController(AdminService adminService,
                               AdminDashboardService adminDashboardService,
                               MonitoringService monitoringService,
                               AwsService awsService,
                               ReportAdminService reportAdminService,
                               UserService userService,
                               S3Service s3Service) {
        this.adminService = adminService;
        this.adminDashboardService = adminDashboardService;
        this.monitoringService = monitoringService;
        this.awsService = awsService;
        this.reportAdminService = reportAdminService;
        this.userService = userService;
        this.s3Service = s3Service;
    }

    @GetMapping("/dashboard")
    public AdminDashboardData dashboard() {
        return adminDashboardService.fetchDashboardData();
    }

    @GetMapping("/review-items")
    public List<AdminReviewItemDto> reviewItems() {
        return adminService.getPendingReviewItems().stream()
                .map(AdminReviewItemDto::from)
                .toList();
    }

    @PostMapping("/review-items/{id}/approve")
    public void approveReview(@PathVariable Long id) {
        adminService.approveReviewItem(id);
    }

    @PostMapping("/review-items/{id}/reject")
    public void rejectReview(@PathVariable Long id) {
        adminService.rejectReviewItem(id);
    }

    @PostMapping("/review-items/{id}/delete")
    public void deleteReview(@PathVariable Long id) {
        adminService.deleteReviewItem(id);
    }

    @GetMapping("/review-items/{id}")
    public AdminReviewItemDetailDto reviewItem(@PathVariable Long id) {
        return adminService.getReviewItemDetail(id);
    }

    @GetMapping("/users")
    public List<UserSummaryDto> users() {
        return adminService.getUsers();
    }

    @GetMapping("/users/{id}")
    public UserDetailDto userDetail(@PathVariable Long id) {
        return adminService.getUserDetail(id);
    }

    @PostMapping("/users/{id}/block")
    public void blockUser(@PathVariable Long id, @RequestBody(required = false) UserWarningRequest request) {
        adminService.blockUser(id, request != null ? request.safeMessage() : null);
    }

    @PostMapping("/users/{id}/unblock")
    public void unblockUser(@PathVariable Long id) {
        adminService.unblockUser(id);
    }

    @PostMapping("/users/{id}/warn")
    public DisciplinaryService.DisciplinaryResult warnUser(@PathVariable Long id,
                                                           @Valid @RequestBody UserWarningRequest request) {
        return adminService.warnUser(id, request.safeMessage(), request.safeLink());
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
    }

    @GetMapping("/posts")
    public List<PostSummaryDto> posts(@RequestParam(value = "status", required = false) PostStatus status,
                                      @RequestParam(value = "authorId", required = false) Long authorId) {
        return adminService.getPosts(status, authorId);
    }

    @PostMapping("/posts/{id}/status")
    public void updatePostStatus(@PathVariable Long id, @RequestBody PostStatusUpdateRequest request) {
        adminService.updatePostStatus(id, request.status());
    }

    @PostMapping("/posts/{id}/delete")
    public void deletePost(@PathVariable Long id) {
        adminService.deletePost(id);
    }

    @GetMapping("/posts/{id}")
    public AdminPostDetailDto postDetail(@PathVariable Long id) {
        return adminService.getPostDetail(id);
    }

    @PostMapping("/notices")
    public NoticeResponseDto createNotice(@Valid @RequestBody NoticeRequestDto requestDto) {
        return adminService.createNotice(requestDto);
    }

    @GetMapping("/notices")
    public List<NoticeResponseDto> notices() {
        return adminService.getNotices();
    }

    @GetMapping("/notices/{id}")
    public NoticeResponseDto notice(@PathVariable Long id) {
        return adminService.getNotice(id);
    }

    @PostMapping("/notices/{id}")
    public NoticeResponseDto updateNotice(@PathVariable Long id,
                                          @Valid @RequestBody NoticeRequestDto requestDto) {
        return adminService.updateNotice(id, requestDto);
    }

    @DeleteMapping("/notices/{id}")
    public void deleteNotice(@PathVariable Long id) {
        adminService.deleteNotice(id);
    }

    @PostMapping("/notices/{id}/pin")
    public NoticeResponseDto pinNotice(@PathVariable Long id, @RequestParam("pinned") boolean pinned) {
        return adminService.setNoticePinned(id, pinned);
    }

    @PostMapping("/notices/upload")
    public PresignedUploadResponse createNoticeUpload(@RequestBody(required = false) PresignedUploadRequest request) {
        String contentType = request != null ? request.contentType() : null;
        S3Service.PresignedUpload presigned = s3Service.generatePresignedUpload("notices", contentType);
        return PresignedUploadResponse.from(presigned);
    }

    @GetMapping("/monitoring/metrics")
    public MonitoringMetricsDto monitoringMetrics() {
        return monitoringService.getCurrentMetrics();
    }

    @GetMapping("/monitoring/s3")
    public List<S3ObjectDto> listS3Objects() {
        try {
            return awsService.listBucketObjects();
        } catch (Exception ex) {
            log.warn("Failed to load S3 objects for admin monitoring", ex);
            return List.of();
        }
    }

    @GetMapping("/monitoring/rekognition")
    public List<RekognitionLabelDto> detectUnsafe(@RequestParam("objectKey") String objectKey) {
        try {
            return awsService.detectUnsafeImage(objectKey);
        } catch (Exception ex) {
            log.warn("Failed to request Rekognition labels. objectKey={}", objectKey, ex);
            return List.of();
        }
    }

    @GetMapping("/reports")
    public List<ReportSummaryDto> reports(@RequestParam(value = "status", required = false) ReportStatus status) {
        return adminService.getReports(status);
    }

    @GetMapping("/reports/{id}")
    public ReportDetailDto reportDetail(@PathVariable Long id) {
        return adminService.getReportDetail(id);
    }

    @PostMapping("/reports/{id}/approve")
    public ReportDetailDto approveReport(@AuthenticationPrincipal UserDetails userDetails,
                                         @PathVariable Long id,
                                         @RequestBody(required = false) ReportActionRequest request) {
        User admin = userService.findByUsername(userDetails.getUsername());
        String note = request != null ? request.note() : null;
        reportAdminService.approve(id, admin, note);
        return adminService.getReportDetail(id);
    }

    @PostMapping("/reports/{id}/block")
    public ReportDetailDto blockReportTarget(@AuthenticationPrincipal UserDetails userDetails,
                                             @PathVariable Long id,
                                             @RequestBody(required = false) ReportActionRequest request) {
        User admin = userService.findByUsername(userDetails.getUsername());
        String note = request != null ? request.note() : null;
        reportAdminService.hideOnly(id, admin, note);
        return adminService.getReportDetail(id);
    }

    @PostMapping("/reports/{id}/reject")
    public ReportDetailDto rejectReport(@AuthenticationPrincipal UserDetails userDetails,
                                        @PathVariable Long id,
                                        @RequestBody(required = false) ReportActionRequest request) {
        User admin = userService.findByUsername(userDetails.getUsername());
        String note = request != null ? request.note() : null;
        reportAdminService.reject(id, admin, note);
        return adminService.getReportDetail(id);
    }

    public record ReportActionRequest(String note) {
    }
}
