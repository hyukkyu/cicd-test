package com.example.authapp.controller;

import com.example.authapp.post.Post;
import com.example.authapp.post.PostService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class SearchController {

    private final PostService postService;

    public SearchController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/search")
    public String search(@RequestParam("kw") String kw,
                         @RequestParam(value = "galleryPage", defaultValue = "0") int galleryPage,
                         @RequestParam(value = "freeboardPage", defaultValue = "0") int freeboardPage,
                         Model model) {
        Map<String, Page<Post>> results = postService.searchGalleryAndFreeboard(kw, galleryPage, freeboardPage);
        model.addAttribute("galleryPosts", results.get("gallery"));
        model.addAttribute("freeboardPosts", results.get("freeboard"));
        model.addAttribute("keyword", kw);
        model.addAttribute("galleryPage", galleryPage);
        model.addAttribute("freeboardPage", freeboardPage);
        return "search_result";
    }
}
