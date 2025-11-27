package com.example.authapp.report;

import com.example.authapp.comment.Comment;
import com.example.authapp.comment.CommentService;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostService;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class ReportPageController {

    private final PostService postService;
    private final ReportService reportService;
    private final UserService userService;
    private final CommentService commentService;

    public ReportPageController(PostService postService, ReportService reportService, UserService userService, CommentService commentService) {
        this.postService = postService;
        this.reportService = reportService;
        this.userService = userService;
        this.commentService = commentService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/post/report/{postId}")
    public String showReportForm(@PathVariable Long postId, Model model, Principal principal) {
        Post post = postService.getPost(postId);
        ReportDto reportDto = new ReportDto();
        reportDto.setTargetId(postId);
        reportDto.setType(ReportType.POST);

        addCommonAttributes(model, principal);
        model.addAttribute("post", post);
        model.addAttribute("reportDto", reportDto);
        return "post_report";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/post/report/{postId}")
    public String submitReport(@PathVariable Long postId,
                               @ModelAttribute("reportDto") ReportDto reportDto,
                               Principal principal,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        Post post = postService.getPost(postId);
        reportDto.setTargetId(postId);
        reportDto.setType(ReportType.POST);

        User reporter = userService.findByUsername(principal.getName());
        String reason = reportDto.getReason() != null ? reportDto.getReason().trim() : "";
        reportDto.setReason(reason);

        if (reason.isEmpty()) {
            addCommonAttributes(model, principal);
            model.addAttribute("post", post);
            model.addAttribute("reportDto", reportDto);
            model.addAttribute("reportError", "신고 사유를 입력해주세요.");
            return "post_report";
        }

        boolean success = reportService.create(postId, ReportType.POST, reason, reporter);
        if (success) {
            redirectAttributes.addFlashAttribute("reportSuccess", true);
            return "redirect:/post/detail/" + postId;
        } else {
            addCommonAttributes(model, principal);
            model.addAttribute("post", post);
            model.addAttribute("reportDto", reportDto);
            model.addAttribute("reportError", "이미 신고한 게시글입니다.");
            return "post_report";
        }
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/comment/report/{commentId}")
    public String showCommentReportForm(@PathVariable Long commentId, Model model, Principal principal) {
        Comment comment = commentService.getComment(commentId);
        ReportDto reportDto = new ReportDto();
        reportDto.setTargetId(commentId);
        reportDto.setType(ReportType.COMMENT);

        addCommonAttributes(model, principal);
        model.addAttribute("comment", comment);
        model.addAttribute("reportDto", reportDto);
        return "comment_report";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/comment/report/{commentId}")
    public String submitCommentReport(@PathVariable Long commentId,
                                      @ModelAttribute("reportDto") ReportDto reportDto,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        Comment comment = commentService.getComment(commentId);
        reportDto.setTargetId(commentId);
        reportDto.setType(ReportType.COMMENT);

        User reporter = userService.findByUsername(principal.getName());
        String reason = reportDto.getReason() != null ? reportDto.getReason().trim() : "";
        reportDto.setReason(reason);

        if (reason.isEmpty()) {
            addCommonAttributes(model, principal);
            model.addAttribute("comment", comment);
            model.addAttribute("reportDto", reportDto);
            model.addAttribute("reportError", "신고 사유를 입력해주세요.");
            return "comment_report";
        }

        boolean success = reportService.create(commentId, ReportType.COMMENT, reason, reporter);
        if (success) {
            redirectAttributes.addFlashAttribute("reportSuccess", true);
            return "redirect:/post/detail/" + comment.getPost().getId();
        } else {
            addCommonAttributes(model, principal);
            model.addAttribute("comment", comment);
            model.addAttribute("reportDto", reportDto);
            model.addAttribute("reportError", "이미 신고한 댓글입니다.");
            return "comment_report";
        }
    }

    private void addCommonAttributes(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
    }
}
