package com.example.authapp.notice;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Notice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createDate;

    @Column(nullable = false)
    private LocalDateTime updateDate;

    @Column(nullable = false)
    private boolean pinned = false;

    private LocalDateTime pinnedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notice_attachments", joinColumns = @JoinColumn(name = "notice_id"))
    @Column(name = "url", columnDefinition = "TEXT")
    private List<String> attachmentUrls = new ArrayList<>();

    public void update(String title, String content, boolean pinned, List<String> attachmentUrls) {
        this.title = title;
        this.content = content;
        setPinned(pinned);
        setAttachmentUrls(attachmentUrls);
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        if (pinned) {
            this.pinnedAt = LocalDateTime.now();
        } else {
            this.pinnedAt = null;
        }
    }

    public void setAttachmentUrls(List<String> attachmentUrls) {
        this.attachmentUrls = attachmentUrls != null ? new ArrayList<>(attachmentUrls) : new ArrayList<>();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createDate = now;
        this.updateDate = now;
        if (pinned && pinnedAt == null) {
            this.pinnedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateDate = LocalDateTime.now();
        if (pinned && pinnedAt == null) {
            this.pinnedAt = this.updateDate;
        }
    }
}
