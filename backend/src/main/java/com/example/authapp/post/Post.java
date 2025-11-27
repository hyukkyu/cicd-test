package com.example.authapp.post;

import com.example.authapp.comment.Comment;
import com.example.authapp.image.ImageModeration;
import com.example.authapp.user.User;
import com.example.authapp.video.VideoModeration;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.CascadeType;
import javax.persistence.Enumerated;
import javax.persistence.EnumType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String mainBoardName;

    private String subBoardName;

    private String tabItem;

    private LocalDateTime createDate;

    @ManyToOne
    private User author;

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE)
    private List<Comment> commentList = new ArrayList<>();

    @ManyToMany
    private Set<User> voter = new HashSet<>();

    private int viewCount;

    @Column(name = "blocked", nullable = false)
    private boolean blocked = false;

    @Convert(converter = PostStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status = PostStatus.PUBLISHED;

    @Column(name = "harmful", nullable = false)
    private boolean harmful = false;

    private LocalDateTime reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20, columnDefinition = "varchar(20) default 'VISIBLE'")
    private PostModerationStatus moderationStatus = PostModerationStatus.VISIBLE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_file_urls", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "file_url")
    private List<String> fileUrls = new ArrayList<>();

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
    private VideoModeration videoModeration;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ImageModeration> imageModerations = new ArrayList<>();

    @OneToOne(mappedBy = "post", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true, orphanRemoval = true)
    private TextModeration textModeration;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = PostStatus.PUBLISHED;
        }
        if (moderationStatus == null) {
            moderationStatus = PostModerationStatus.VISIBLE;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMainBoardName() {
        return mainBoardName;
    }

    public void setMainBoardName(String mainBoardName) {
        this.mainBoardName = mainBoardName;
    }

    public String getSubBoardName() {
        return subBoardName;
    }

    public void setSubBoardName(String subBoardName) {
        this.subBoardName = subBoardName;
    }

    public String getTabItem() {
        return tabItem;
    }

    public void setTabItem(String tabItem) {
        this.tabItem = tabItem;
    }

    public LocalDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(LocalDateTime createDate) {
        this.createDate = createDate;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public List<Comment> getCommentList() {
        return commentList;
    }

    public void setCommentList(List<Comment> commentList) {
        this.commentList = commentList;
    }

    public Set<User> getVoter() {
        return voter;
    }

    public void setVoter(Set<User> voter) {
        this.voter = voter;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }

    public boolean isHarmful() {
        return harmful;
    }

    public void setHarmful(boolean harmful) {
        this.harmful = harmful;
    }

    public LocalDateTime getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }

    public PostModerationStatus getModerationStatus() {
        return moderationStatus != null ? moderationStatus : PostModerationStatus.VISIBLE;
    }

    public void setModerationStatus(PostModerationStatus moderationStatus) {
        this.moderationStatus = moderationStatus != null ? moderationStatus : PostModerationStatus.VISIBLE;
    }

    public List<String> getFileUrls() {
        return fileUrls;
    }

    public void setFileUrls(List<String> fileUrls) {
        this.fileUrls = fileUrls;
    }

    public VideoModeration getVideoModeration() {
        return videoModeration;
    }

    public void setVideoModeration(VideoModeration videoModeration) {
        this.videoModeration = videoModeration;
    }

    public List<ImageModeration> getImageModerations() {
        return imageModerations;
    }

    public void setImageModerations(List<ImageModeration> imageModerations) {
        this.imageModerations = imageModerations;
    }

    public TextModeration getTextModeration() {
        return textModeration;
    }

    public void setTextModeration(TextModeration textModeration) {
        this.textModeration = textModeration;
        if (textModeration != null) {
            textModeration.setPost(this);
        }
    }

    public void markReported(boolean harmfulDetected) {
        this.status = PostStatus.REPORTED;
        this.harmful = harmfulDetected;
        this.reportedAt = LocalDateTime.now();
        this.moderationStatus = PostModerationStatus.REVIEW;
    }

    public void remove() {
        this.status = PostStatus.REMOVED;
        this.blocked = true;
        this.moderationStatus = PostModerationStatus.BLOCKED;
    }

    public void hide() {
        this.status = PostStatus.HIDDEN;
        this.blocked = true;
        this.reportedAt = LocalDateTime.now();
        this.moderationStatus = PostModerationStatus.BLOCKED;
    }

    public void restore() {
        this.status = PostStatus.PUBLISHED;
        this.blocked = false;
        this.harmful = false;
        this.reportedAt = null;
        this.moderationStatus = PostModerationStatus.VISIBLE;
    }
}
