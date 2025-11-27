package com.example.authapp.comment;

import com.example.authapp.common.api.PageResponse;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostService;
import com.example.authapp.post.dto.CommentResponse;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/comments")
@Validated
public class CommentRestController {

    private final PostService postService;
    private final CommentService commentService;
    private final CommentRepository commentRepository;
    private final UserService userService;

    public CommentRestController(PostService postService,
                                 CommentService commentService,
                                 CommentRepository commentRepository,
                                 UserService userService) {
        this.postService = postService;
        this.commentService = commentService;
        this.commentRepository = commentRepository;
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<CommentResponse> list(@RequestParam Long postId,
                                              @RequestParam(value = "page", defaultValue = "0") int page,
                                              @RequestParam(value = "size", defaultValue = "20") int size,
                                              @RequestParam(value = "sort", defaultValue = "oldest") String sort) {
        Sort.Direction direction = "latest".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "createDate"));
        var statuses = List.of(CommentStatus.VISIBLE, CommentStatus.BLOCKED);
        var pageResult = commentRepository.findByPostIdAndStatusIn(postId, statuses, pageable);
        var content = pageResult.stream()
                .map(CommentResponse::from)
                .toList();
        return PageResponse.of(pageResult, content);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal UserDetails userDetails,
                                                  @Valid @RequestBody CommentCreateRequest request) {
        Post post = postService.getPostForEdit(request.postId());
        User author = userService.findByUsername(userDetails.getUsername());
        Comment parent = request.parentId() != null ? commentService.getComment(request.parentId()) : null;
        Comment saved = commentService.create(post, request.content(), author, parent);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(saved));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public CommentResponse update(@AuthenticationPrincipal UserDetails userDetails,
                                  @PathVariable Long id,
                                  @Valid @RequestBody CommentUpdateRequest request) {
        Comment comment = commentService.getComment(id);
        if (comment.getAuthor() == null || !comment.getAuthor().getUsername().equals(userDetails.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 수정 권한이 없습니다.");
        }
        commentService.update(comment, request.content());
        return CommentResponse.from(comment);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long id) {
        Comment comment = commentService.getComment(id);
        if (comment.getAuthor() == null || !comment.getAuthor().getUsername().equals(userDetails.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "댓글 삭제 권한이 없습니다.");
        }
        commentService.delete(comment);
        return ResponseEntity.noContent().build();
    }
}
