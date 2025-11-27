package com.example.authapp.notice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeRestController {

    private final NoticeService noticeService;

    public NoticeRestController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public List<Notice> list() {
        return noticeService.findAll();
    }

    @GetMapping("/{id}")
    public Notice detail(@PathVariable Long id) {
        return noticeService.getNotice(id);
    }
}
