package com.example.authapp.comment;

import com.example.authapp.notification.UserNotificationService;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostService;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.security.Principal;

@RequestMapping("/comment")
@Controller
public class CommentController {

    private final PostService postService;
    private final UserService userService;
    private final CommentService commentService;
    private final UserNotificationService userNotificationService;

    public CommentController(PostService postService,
                             UserService userService,
                             CommentService commentService,
                             UserNotificationService userNotificationService) {
        this.postService = postService;
        this.userService = userService;
        this.commentService = commentService;
        this.userNotificationService = userNotificationService;
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create/{id}")
    public String createComment(Model model, @PathVariable("id") Long id, 
                                @RequestParam(value="parentId", required=false) Long parentId,
                                @Valid CommentForm commentForm, BindingResult bindingResult, Principal principal) {
        Post post = this.postService.getPost(id);
        User author = this.userService.findByUsername(principal.getName());
        if (bindingResult.hasErrors()) {
            model.addAttribute("post", post);
            model.addAttribute("currentUser", author);

            String currentUsername = author.getUsername();
            model.addAttribute("currentUsername", currentUsername);

            boolean isAuthor = post.getAuthor() != null && post.getAuthor().getUsername().equals(currentUsername);
            model.addAttribute("isAuthor", isAuthor);

            boolean isVoted = post.getVoter().contains(author);
            model.addAttribute("isVoted", isVoted);

            String listUrl = "/post/list";
            if (post.getMainBoardName() != null && post.getSubBoardName() != null
                    && !"자유".equalsIgnoreCase(post.getMainBoardName())) {
                listUrl = String.format("/post/list/%s/%s", post.getMainBoardName(), post.getSubBoardName());
            }
            model.addAttribute("listUrl", listUrl);
            return "post_detail";
        }
        Comment parentComment = null;
        if (parentId != null) {
            parentComment = this.commentService.getComment(parentId);
        }
        this.commentService.create(post, commentForm.getContent(), author, parentComment);
        return String.format("redirect:/post/detail/%s", id);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/modify/{id}")
    public String commentModify(CommentForm commentForm, @PathVariable("id") Long id, Principal principal) {
        Comment comment = this.commentService.getComment(id);
        if (!comment.getAuthor().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정권한이 없습니다.");
        }
        commentForm.setContent(comment.getContent());
        return "comment_form";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/modify/{id}")
    public String commentModify(@Valid CommentForm commentForm, BindingResult bindingResult, 
                                @PathVariable("id") Long id, Principal principal) {
        if (bindingResult.hasErrors()) {
            return "comment_form";
        }
        Comment comment = this.commentService.getComment(id);
        if (!comment.getAuthor().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정권한이 없습니다.");
        }
        this.commentService.update(comment, commentForm.getContent());
        return String.format("redirect:/post/detail/%s", comment.getPost().getId());
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/delete/{id}")
    public String commentDelete(Principal principal, @PathVariable("id") Long id) {
        Comment comment = this.commentService.getComment(id);
        if (!comment.getAuthor().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제권한이 없습니다.");
        }
        User author = comment.getAuthor();
        String snippet = comment.getContent() != null && comment.getContent().length() > 20
                ? comment.getContent().substring(0, 20) + "..."
                : comment.getContent();
        Long postId = comment.getPost() != null ? comment.getPost().getId() : null;
        this.commentService.delete(comment);
        if (author != null) {
            String message = String.format("댓글%s 삭제 요청이 완료되었습니다.",
                    snippet != null && !snippet.isBlank() ? " '" + snippet.trim() + "'" : "");
            String link = postId != null ? "/post/detail/" + postId : "/post/list";
            userNotificationService.notifyContentAction(author, message, link);
        }
        return postId != null ? String.format("redirect:/post/detail/%s", postId) : "redirect:/post/list";
    }
}
