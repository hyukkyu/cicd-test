package com.example.authapp.post;

import com.example.authapp.notification.UserNotificationService;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import com.example.authapp.comment.CommentForm;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest; // Added this line
import javax.validation.Valid;
import java.security.Principal;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

import com.example.authapp.s3.S3Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RequestMapping("/post")
@Controller
public class PostController {

    private final PostService postService;
    private final UserService userService;
    private final S3Service s3Service;
    private final UserNotificationService userNotificationService;

    public PostController(PostService postService,
                          UserService userService,
                          S3Service s3Service,
                          UserNotificationService userNotificationService) {
        this.postService = postService;
        this.userService = userService;
        this.s3Service = s3Service;
        this.userNotificationService = userNotificationService;
    }

    @GetMapping("/list")
    public String list(Model model, @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "searchType", required = false, defaultValue = "all") String searchType,
                       @RequestParam(value = "kw", required = false, defaultValue = "") String kw,
                       @RequestParam(value = "postType", defaultValue = "normal") String postType, HttpServletRequest request, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        model.addAttribute("boardName", "자유게시판");
        model.addAttribute("mainBoardName", "자유");
        model.addAttribute("subBoardName", "자유");
        model.addAttribute("baseUrl", "/post/list");
        model.addAttribute("kw", kw);
        model.addAttribute("searchType", searchType);

        boolean isPopular = "popular".equals(postType);
        model.addAttribute("isPopular", isPopular);
        model.addAttribute("postType", postType);

        Page<Post> paging = this.postService.getListByCategory("자유", page, searchType, kw, isPopular);
        model.addAttribute("paging", paging);
        return "post_list";
    }

    @GetMapping("/list/{mainBoardName}/{subBoardName}")
    public String board(@PathVariable("mainBoardName") String mainBoardName, 
                      @PathVariable("subBoardName") String subBoardName, 
                      @RequestParam(value = "page", defaultValue = "0") int page,
                      @RequestParam(value = "tab", defaultValue = "전체") String tab,
                      @RequestParam(value = "searchType", required = false, defaultValue = "all") String searchType,
                      @RequestParam(value = "kw", required = false, defaultValue = "") String kw,
                      @RequestParam(value = "postType", defaultValue = "normal") String postType,
                      Model model, HttpServletRequest request, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        if ("".equals(tab)) {
            tab = null;
        }
        model.addAttribute("boardName", subBoardName + " 게시판");
        model.addAttribute("mainBoardName", mainBoardName);
        model.addAttribute("subBoardName", subBoardName);
        model.addAttribute("activeTab", tab);
        model.addAttribute("kw", kw);
        model.addAttribute("searchType", searchType);
        model.addAttribute("baseUrl", String.format("/post/list/%s/%s", mainBoardName, subBoardName));
        
        boolean isPopular = "popular".equals(postType);
        model.addAttribute("isPopular", isPopular);
        model.addAttribute("postType", postType);

        Page<Post> paging;
        if (tab != null && !tab.isEmpty() && !tab.equals("전체")) {
            paging = this.postService.getListByCategoryAndTab(subBoardName, tab, page, searchType, kw, isPopular);
        }
        else {
            paging = this.postService.getListByCategory(subBoardName, page, searchType, kw, isPopular);
        }
        model.addAttribute("paging", paging);
        
        String templateName;
        switch (mainBoardName) {
            case "game":
                templateName = "game_board";
                break;
            case "travel":
                templateName = "travel_board";
                break;
            case "exercise":
                templateName = "exercise_board";
                break;
            case "movie":
                templateName = "movie_board";
                break;
            case "music":
                templateName = "music_board";
                break;
            case "invest":
                templateName = "invest_board";
                break;
            default:
                return "redirect:/post/list"; // Redirect to free board if main category is not recognized
        }
        
        return templateName;
    }

    @GetMapping(value = "/detail/{id}")
    public String detail(Model model, @PathVariable("id") Long id, CommentForm commentForm, Principal principal) {
        String currentUsername = null;
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
            currentUsername = currentUser.getUsername();
        }
        Post post = this.postService.getPost(id);

        boolean isVoted = false;
        if (principal != null) {
            try {
                User user = this.userService.findByUsername(principal.getName());
                if (post.getVoter().contains(user)) {
                    isVoted = true;
                }
            } catch (Exception e) {
                // User not found or other issue, treat as not voted
            }
        }

        model.addAttribute("post", post);
        model.addAttribute("isVoted", isVoted);
        model.addAttribute("currentUsername", currentUsername);
        boolean isAuthor = currentUsername != null && post.getAuthor() != null
                && currentUsername.equals(post.getAuthor().getUsername());
        model.addAttribute("isAuthor", isAuthor);

        String listUrl = "/post/list";
        if (post.getMainBoardName() != null && post.getSubBoardName() != null
                && !"자유".equalsIgnoreCase(post.getMainBoardName())) {
            listUrl = String.format("/post/list/%s/%s", post.getMainBoardName(), post.getSubBoardName());
        }
        model.addAttribute("listUrl", listUrl);
        return "post_detail";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/create")
    public String postCreate(PostForm postForm, Model model, 
                             @RequestParam(value = "mainBoardName", required = false) String mainBoardName,
                             @RequestParam(value = "subBoardName", required = false) String subBoardName, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        if (subBoardName != null) {
            postForm.setSubBoardName(subBoardName); 
        }
        if (mainBoardName != null) {
            postForm.setMainBoardName(mainBoardName);
        }
        List<String> tabItems;
        if ("자유".equals(mainBoardName)) {
            tabItems = java.util.Collections.emptyList();
        } else {
            tabItems = new java.util.ArrayList<>(postService.getTabItemsForCategory(mainBoardName));
            if ("invest".equals(mainBoardName)) {
                tabItems.remove("전체");
            }
        }
        model.addAttribute("tabItems", tabItems);
        model.addAttribute("mainBoardName", mainBoardName); // Still pass original mainBoardName to model
        model.addAttribute("subBoardName", subBoardName);   // Still pass original subBoardName to model
        model.addAttribute("formAction", "/post/create");
        return "post_form";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/create")
    public String postCreate(@Valid PostForm postForm, BindingResult bindingResult, Principal principal, Model model,
                             @RequestParam("files") List<MultipartFile> files) throws IOException {
        if (!"자유".equals(postForm.getMainBoardName())) {
            if (postForm.getTabItem() == null || postForm.getTabItem().isEmpty()) {
                bindingResult.rejectValue("tabItem", "NotEmpty", "탭은 필수항목입니다.");
            }
        }

        if (bindingResult.hasErrors()) {
            List<String> tabItems;
            if ("자유".equals(postForm.getMainBoardName())) {
                tabItems = java.util.Collections.emptyList();
            } else {
                tabItems = new java.util.ArrayList<>(postService.getTabItemsForCategory(postForm.getMainBoardName()));
                if ("invest".equals(postForm.getMainBoardName())) {
                    tabItems.remove("전체");
                }
            }
            model.addAttribute("tabItems", tabItems);
            model.addAttribute("mainBoardName", postForm.getMainBoardName());
            model.addAttribute("subBoardName", postForm.getSubBoardName());
            model.addAttribute("formAction", "/post/create");
            return "post_form";
        }

        List<String> fileUrls = new java.util.ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    fileUrls.add(s3Service.uploadPostMedia(file));
                }
            }
        }

        User author = this.userService.findByUsername(principal.getName());
        
        String mainBoardName = postForm.getMainBoardName();
        String subBoardName = postForm.getSubBoardName();

        this.postService.create(postForm.getTitle(), postForm.getContent(), mainBoardName, subBoardName, postForm.getTabItem(), author, fileUrls);
        
        // Dynamic redirect
        // mainBoardName and subBoardName are already decoded by Spring's data binder

        if ("자유".equals(subBoardName)) {
            return "redirect:/post/list";
        }

        if (mainBoardName != null && !mainBoardName.isEmpty() && subBoardName != null && !subBoardName.isEmpty()) {
            try {
                String encodedMainBoardName = java.net.URLEncoder.encode(mainBoardName, "UTF-8");
                String encodedSubBoardName = java.net.URLEncoder.encode(subBoardName, "UTF-8");
                String tabItem = postForm.getTabItem();
                String encodedTabItem = java.net.URLEncoder.encode("전체", "UTF-8");
                return String.format("redirect:/post/list/%s/%s?tab=%s", encodedMainBoardName, encodedSubBoardName, encodedTabItem);
            } catch (UnsupportedEncodingException e) {
                // Fallback or error handling
                return "redirect:/post/list";
            }
        }
        
        return "redirect:/post/list"; // Default fallback
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/modify/{id}")
    public String postModify(PostForm postForm, @PathVariable("id") Long id, Principal principal, Model model) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        Post post = this.postService.getPost(id);
        if (!post.getAuthor().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정권한이 없습니다.");
        }
        postForm.setTitle(post.getTitle());
        postForm.setContent(post.getContent());
        postForm.setMainBoardName(post.getMainBoardName()); 
        postForm.setSubBoardName(post.getSubBoardName());
        postForm.setTabItem(post.getTabItem());
        List<String> tabItems = postService.getTabItemsForCategory(post.getMainBoardName()); 
        model.addAttribute("tabItems", tabItems);
        model.addAttribute("formAction", "/post/modify/" + id);
        return "post_form";
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/modify/{id}")
    public String postModify(@Valid PostForm postForm, BindingResult bindingResult,
                                 Principal principal, @PathVariable("id") Long id, Model model,
                                 @RequestParam("files") List<MultipartFile> files) throws IOException {
        if (bindingResult.hasErrors()) {
            List<String> tabItems = postService.getTabItemsForCategory(postForm.getMainBoardName());
            model.addAttribute("tabItems", tabItems);
            return "post_form";
        }
        Post post = this.postService.getPost(id);
        if (!post.getAuthor().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정권한이 없습니다.");
        }

        List<String> fileUrls = new java.util.ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    fileUrls.add(s3Service.uploadPostMedia(file));
                }
            }
        }

        String mainBoardName = postForm.getMainBoardName();
        String subBoardName = postForm.getSubBoardName();

        this.postService.update(post, postForm.getTitle(), postForm.getContent(), mainBoardName, subBoardName, postForm.getTabItem(), fileUrls);

        // Dynamic redirect logic (same as postCreate POST)
        // mainBoardName and subBoardName are already decoded by Spring's data binder

        if ("자유".equals(subBoardName)) {
            return "redirect:/post/list";
        }

        if (mainBoardName != null && !mainBoardName.isEmpty() && subBoardName != null && !subBoardName.isEmpty()) {
            try {
                String encodedMainBoardName = java.net.URLEncoder.encode(mainBoardName, "UTF-8");
                String encodedSubBoardName = java.net.URLEncoder.encode(subBoardName, "UTF-8");
                String tabItem = postForm.getTabItem();
                String encodedTabItem = tabItem != null ? java.net.URLEncoder.encode(tabItem, "UTF-8") : "";
                return String.format("redirect:/post/list/%s/%s?tab=%s", encodedMainBoardName, encodedSubBoardName, encodedTabItem);
            } catch (UnsupportedEncodingException e) {
                // Fallback or error handling
                return "redirect:/post/list";
            }
        }

        return String.format("redirect:/post/detail/%s", id); // Fallback to detail page if redirect info is missing
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/delete/{id}")
    public String postDelete(Principal principal, @PathVariable("id") Long id) {
        Post post = this.postService.getPost(id);
        if (!post.getAuthor().getUsername().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제권한이 없습니다.");
        }
        User deleter = post.getAuthor();
        String title = post.getTitle() != null ? post.getTitle() : "제목 없음";
        this.postService.delete(post);
        if (deleter != null) {
            String message = String.format("게시글 '%s' 삭제 요청이 완료되었습니다.", title);
            userNotificationService.notifyContentAction(deleter, message, "/post/list");
        }
        return "redirect:/post/list";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/vote/{id}")
    public String postVote(Principal principal, @PathVariable("id") Long id) {
        Post post = this.postService.getPost(id);
        User user = this.userService.findByUsername(principal.getName());
        this.postService.vote(post, user);
        return String.format("redirect:/post/detail/%s", id);
    }
}
