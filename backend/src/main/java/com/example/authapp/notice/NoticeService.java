package com.example.authapp.notice;

import com.example.authapp.admin.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public Page<Notice> getList(int page) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by(
                Sort.Order.desc("pinned"),
                Sort.Order.desc("pinnedAt"),
                Sort.Order.desc("createDate")
        ));
        return this.noticeRepository.findAll(pageable);
    }

    @Transactional
    public Notice create(String title, String content, boolean pinned, List<String> attachmentUrls) {
        Notice notice = new Notice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setPinned(pinned);
        notice.setAttachmentUrls(attachmentUrls);
        return noticeRepository.save(notice);
    }

    @Transactional
    public Notice update(Long id, String title, String content, boolean pinned, List<String> attachmentUrls) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        notice.update(title, content, pinned, attachmentUrls);
        return notice;
    }

    @Transactional(readOnly = true)
    public Notice getNotice(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        noticeRepository.delete(notice);
    }

    @Transactional
    public Notice setPinned(Long id, boolean pinned) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notice not found: " + id));
        notice.setPinned(pinned);
        return notice;
    }

    @Transactional(readOnly = true)
    public List<Notice> findAll() {
        return noticeRepository.findAll(Sort.by(
                Sort.Order.desc("pinned"),
                Sort.Order.desc("pinnedAt"),
                Sort.Order.desc("createDate")
        ));
    }
}
