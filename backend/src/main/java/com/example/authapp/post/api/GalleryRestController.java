package com.example.authapp.post.api;

import com.example.authapp.comment.CommentRepository;
import com.example.authapp.comment.CommentStatus;
import com.example.authapp.common.api.PageResponse;
import com.example.authapp.post.Post;
import com.example.authapp.post.PostService;
import com.example.authapp.post.dto.CommentResponse;
import com.example.authapp.post.dto.GalleryCardResponse;
import com.example.authapp.post.dto.GalleryDetailResponse;
import com.example.authapp.post.dto.GalleryUploadForm;
import com.example.authapp.post.dto.GalleryWriteRequest;
import com.example.authapp.post.dto.PostDetailResponse;
import com.example.authapp.s3.S3Service;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/gallery")
@Validated
public class GalleryRestController {

    private static final Logger log = LoggerFactory.getLogger(GalleryRestController.class);

    private final PostService postService;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final S3Service s3Service;

    public GalleryRestController(PostService postService,
                                 CommentRepository commentRepository,
                                 UserService userService,
                                 S3Service s3Service) {
        this.postService = postService;
        this.commentRepository = commentRepository;
        this.userService = userService;
        this.s3Service = s3Service;
    }

    @GetMapping
    public PageResponse<GalleryCardResponse> list(@RequestParam(value = "mainBoard", required = false) String mainBoard,
                                                  @RequestParam(value = "subBoard", required = false) String subBoard,
                                                  @RequestParam(value = "tab", required = false) String tab,
                                                  @RequestParam(value = "sort", defaultValue = "latest") String sort,
                                                  @RequestParam(value = "searchType", defaultValue = "all") String searchType,
                                                  @RequestParam(value = "kw", defaultValue = "") String keyword,
                                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                                  @RequestParam(value = "size", defaultValue = "12") int size) {
        PostService.GalleryQuery query = new PostService.GalleryQuery(
                mainBoard,
                subBoard,
                tab,
                sort,
                searchType,
                keyword,
                page,
                size
        );
        var pageResult = postService.getGalleryPosts(query);
        List<CommentStatus> visibleAndBlocked = List.of(CommentStatus.VISIBLE, CommentStatus.BLOCKED);
        var cards = pageResult.stream()
                .map(post -> GalleryCardResponse.from(post, commentRepository.countByPostIdAndStatusIn(post.getId(), visibleAndBlocked)))
                .toList();
        return PageResponse.of(pageResult, cards);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GalleryDetailResponse> detail(@PathVariable Long id,
                                                        @AuthenticationPrincipal UserDetails viewer) {
        Post post = postService.getPost(id);
        List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreateDateAsc(id)
                .stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
        List<CommentStatus> visibleAndBlocked = List.of(CommentStatus.VISIBLE, CommentStatus.BLOCKED);
        long commentCount = commentRepository.countByPostIdAndStatusIn(id, visibleAndBlocked);
        boolean liked = viewer != null && hasLiked(post, viewer.getUsername());
        return ResponseEntity.ok(GalleryDetailResponse.from(post, comments, commentCount, liked));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PostDetailResponse> create(@AuthenticationPrincipal UserDetails userDetails,
                                                     @Valid @RequestBody GalleryWriteRequest request) {
        User author = userService.findByUsername(userDetails.getUsername());
        Post post = postService.create(
                request.title(),
                request.content(),
                request.mainBoardName(),
                request.subBoardName(),
                request.tabItem(),
                author,
                request.attachmentUrls()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostDetailResponse.from(post, List.of()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostDetailResponse> createWithMedia(@AuthenticationPrincipal UserDetails userDetails,
                                                              @Valid GalleryUploadForm form) {
        User author = userService.findByUsername(userDetails.getUsername());
        List<MultipartFile> files = form.getFiles();
        List<S3Service.UploadResult> uploads = new ArrayList<>();
        try {
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    uploads.add(s3Service.uploadWithMeta(file, "gallery"));
                }
            }
            List<String> attachmentUrls = uploads.stream()
                    .map(S3Service.UploadResult::publicUrl)
                    .toList();
            Post post = postService.create(
                    form.getTitle(),
                    form.getContent(),
                    form.getMainBoardName(),
                    form.getSubBoardName(),
                    form.getTabItem(),
                    author,
                    attachmentUrls
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(PostDetailResponse.from(post, List.of()));
        } catch (IOException ioException) {
            cleanupUploads(uploads);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 업로드에 실패했습니다.", ioException);
        } catch (Exception ex) {
            cleanupUploads(uploads);
            if (ex instanceof ResponseStatusException) {
                throw ex;
            }
            log.error("Failed to create gallery post with media", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "갤러리 등록에 실패했습니다.");
        }
    }

    private void cleanupUploads(List<S3Service.UploadResult> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            return;
        }
        for (S3Service.UploadResult upload : uploads) {
            try {
                s3Service.deleteObject(upload.key());
            } catch (Exception cleanupError) {
                log.warn("Failed to clean up uploaded media key={}", upload.key(), cleanupError);
            }
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/{id}")
    public ResponseEntity<PostDetailResponse> update(@AuthenticationPrincipal UserDetails userDetails,
                                                     @PathVariable Long id,
                                                     @Valid @RequestBody GalleryWriteRequest request) {
        Post post = postService.getPostForEdit(id);
        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(userDetails.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "갤러리 글 수정 권한이 없습니다.");
        }
        postService.update(post,
                request.title(),
                request.content(),
                request.mainBoardName(),
                request.subBoardName(),
                request.tabItem(),
                request.attachmentUrls());
        List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreateDateAsc(id)
                .stream()
                .map(CommentResponse::from)
                .toList();
        Post refreshed = postService.getPostWithoutIncrement(id);
        return ResponseEntity.ok(PostDetailResponse.from(refreshed, comments));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long id) {
        Post post = postService.getPostForEdit(id);
        if (post.getAuthor() == null || !post.getAuthor().getUsername().equals(userDetails.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "갤러리 글 삭제 권한이 없습니다.");
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

    @GetMapping("/popular")
    public List<GalleryCardResponse> popular(@RequestParam(value = "size", defaultValue = "8") int size) {
        return postService.getPopularGalleryPosts(size).stream()
                .map(post -> GalleryCardResponse.from(post, commentRepository.countByPostIdAndStatus(post.getId(), CommentStatus.VISIBLE)))
                .toList();
    }

    private boolean hasLiked(Post post, String username) {
        if (post.getVoter() == null) {
            return false;
        }
        return post.getVoter().stream()
                .anyMatch(user -> user != null && username.equals(user.getUsername()));
    }
}
