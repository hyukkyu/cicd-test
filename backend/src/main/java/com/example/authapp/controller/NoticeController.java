package com.example.authapp.controller;

import com.example.authapp.notice.Notice;
import com.example.authapp.notice.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequiredArgsConstructor
@Controller
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/notice/list")
    public String noticeList(Model model, @RequestParam(value="page", defaultValue="0") int page) {
        Page<Notice> paging = this.noticeService.getList(page);
        model.addAttribute("paging", paging);
        return "notice_list"; // This will resolve to src/main/resources/templates/notice_list.html
    }
}
