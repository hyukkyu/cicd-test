package com.example.authapp.moderation.controller;

import com.example.authapp.moderation.dto.ModeratedContentResponse;
import com.example.authapp.moderation.dto.ModerationRequest;
import com.example.authapp.moderation.dto.TextModerationRequest;
import com.example.authapp.moderation.dto.TextModerationResponse;
import com.example.authapp.moderation.service.ContentModerationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/moderation")
@Validated
public class ContentModerationController {

    private final ContentModerationService moderationService;

    public ContentModerationController(ContentModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModeratedContentResponse> submitPost(
            @Valid @RequestPart("request") ModerationRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(moderationService.submitContent(request, file));
    }

    @PostMapping("/text")
    public ResponseEntity<TextModerationResponse> analyzeText(
            @Valid @RequestBody TextModerationRequest request
    ) {
        return ResponseEntity.ok(moderationService.analyzeText(request));
    }

    @GetMapping("/{referenceId}")
    public ResponseEntity<ModeratedContentResponse> getStatus(@PathVariable String referenceId) {
        return ResponseEntity.ok(moderationService.getContent(referenceId));
    }
}
