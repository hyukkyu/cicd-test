package com.example.authapp.moderation;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "moderated_content")
public class ModeratedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, unique = true, updatable = false, length = 64)
    private String referenceId = UUID.randomUUID().toString();

    @Column(nullable = false, length = 150)
    private String title;

    @Lob
    @Column(nullable = false)
    private String body;

    @Column(name = "author_id", nullable = false, length = 64)
    private String authorId;

    @Column(length = 50)
    private String board;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "media_bucket")
    private String mediaBucket;

    @Column(name = "media_key")
    private String mediaKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModerationStatus status = ModerationStatus.PENDING;

    @Column(name = "requires_review")
    private boolean reviewRequested;

    @Column(name = "blocked", nullable = false)
    private boolean blocked;

    @Column(name = "block_reason")
    private String blockReason;

    @Column(name = "text_score")
    private double textScore;

    @Column(name = "media_score")
    private double mediaScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AdminAlert> alerts = new ArrayList<>();

    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMediaBucket() {
        return mediaBucket;
    }

    public void setMediaBucket(String mediaBucket) {
        this.mediaBucket = mediaBucket;
    }

    public String getMediaKey() {
        return mediaKey;
    }

    public void setMediaKey(String mediaKey) {
        this.mediaKey = mediaKey;
    }

    public ModerationStatus getStatus() {
        return status;
    }

    public void setStatus(ModerationStatus status) {
        this.status = status;
    }

    public boolean isReviewRequested() {
        return reviewRequested;
    }

    public void setReviewRequested(boolean reviewRequested) {
        this.reviewRequested = reviewRequested;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void markBlocked(String reason, double textScore, double mediaScore) {
        this.blocked = true;
        this.blockReason = reason;
        this.status = ModerationStatus.BLOCKED;
        this.textScore = textScore;
        this.mediaScore = mediaScore;
    }

    public void markApproved() {
        this.blocked = false;
        this.blockReason = null;
        this.status = ModerationStatus.APPROVED;
    }

    public void markReview(String reason) {
        this.reviewRequested = true;
        this.status = ModerationStatus.REVIEW;
        this.blockReason = reason;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public double getTextScore() {
        return textScore;
    }

    public void setTextScore(double textScore) {
        this.textScore = textScore;
    }

    public double getMediaScore() {
        return mediaScore;
    }

    public void setMediaScore(double mediaScore) {
        this.mediaScore = mediaScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<AdminAlert> getAlerts() {
        return alerts;
    }

    public void addAlert(AdminAlert alert) {
        this.alerts.add(alert);
        alert.setContent(this);
    }
}
