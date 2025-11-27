package com.example.authapp.post.api;

import com.example.authapp.comment.CommentRepository;
import com.example.authapp.common.api.PageResponse;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostService;
import com.example.authapp.post.dto.CommentResponse;
import com.example.authapp.post.dto.PostDetailResponse;
import com.example.authapp.post.dto.PostRequest;
import com.example.authapp.post.dto.PostSummaryResponse;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostRestController {

    private final PostService postService;
    private final CommentRepository commentRepository;
    private final UserService userService;

    public PostRestController(PostService postService,
                              CommentRepository commentRepository,
                              UserService userService) {
        this.postService = postService;
        this.commentRepository = commentRepository;
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<PostSummaryResponse> list(@RequestParam(value = "subBoard", required = false) String subBoardName,
                                                  @RequestParam(value = "tab", required = false) String tab,
                                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                                  @RequestParam(value = "searchType", defaultValue = "all") String searchType,
                                                  @RequestParam(value = "kw", defaultValue = "") String keyword,
                                                  @RequestParam(value = "popular", defaultValue = "false") boolean popular) {
        PageResponse<PostSummaryResponse> response;

        if (StringUtils.hasText(subBoardName)) {
            var pageResult = resolveBoardPage(subBoardName, tab, page, searchType, keyword, popular);
            var summaries = pageResult
                    .stream()
                    .map(PostSummaryResponse::from)
                    .toList();
            response = PageResponse.of(pageResult, summaries);
        } else {
            var pageResult = postService.getList(page, searchType, keyword);
            var summaries = pageResult
                    .stream()
                    .map(PostSummaryResponse::from)
                    .toList();
            response = PageResponse.of(pageResult, summaries);
        }

        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDetailResponse> detail(@PathVariable Long id) {
        try {
            Post post = postService.getPost(id);
            List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreateDateAsc(id)
                    .stream()
                    .map(CommentResponse::from)
                    .toList();
            return ResponseEntity.ok(PostDetailResponse.from(post, comments));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found", ex);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<PostDetailResponse> create(@AuthenticationPrincipal UserDetails userDetails,
                                                     @Valid @RequestBody PostRequest request) {
        User author = userService.findByUsername(userDetails.getUsername());
        Post post = postService.create(
                request.title(),
                request.content(),
                request.mainBoardName(),
                request.subBoardName(),
                request.tabItem(),
                author,
                request.fileUrls()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostDetailResponse.from(post, List.of()));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public PostDetailResponse update(@AuthenticationPrincipal UserDetails userDetails,
                                     @PathVariable Long id,
                                     @Valid @RequestBody PostRequest request) {
        Post post = postService.getPostForEdit(id);
        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(userDetails.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "게시글 수정 권한이 없습니다.");
        }
        postService.update(post,
                request.title(),
                request.content(),
                request.mainBoardName(),
                request.subBoardName(),
                request.tabItem(),
                request.fileUrls());
        List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreateDateAsc(id)
                .stream()
                .map(CommentResponse::from)
                .toList();
        Post refreshed = postService.getPostWithoutIncrement(id);
        return PostDetailResponse.from(refreshed, comments);
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long id) {
        Post post = postService.getPostForEdit(id);
        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(userDetails.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "게시글 삭제 권한이 없습니다.");
        }
        postService.delete(post);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/vote")
    public ResponseEntity<Void> vote(@AuthenticationPrincipal UserDetails userDetails,
                                     @PathVariable Long id) {
        Post post = postService.getPostForEdit(id);
        User user = userService.findByUsername(userDetails.getUsername());
        postService.vote(post, user);
        return ResponseEntity.ok().build();
    }

    private org.springframework.data.domain.Page<Post> resolveBoardPage(String subBoardName,
                                                                        String tab,
                                                                        int page,
                                                                        String searchType,
                                                                        String keyword,
                                                                        boolean popular) {
        if (StringUtils.hasText(tab) && !"전체".equals(tab)) {
            return postService.getListByCategoryAndTab(subBoardName, tab, page, searchType, keyword, popular);
        }
        return postService.getListByCategory(subBoardName, page, searchType, keyword, popular);
    }
}
