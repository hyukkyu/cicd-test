package com.example.authapp.moderation;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderation_admin_alerts")
public class AdminAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private ModeratedContent content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AdminAlertType type = AdminAlertType.TEXT;

    @Column(nullable = false, length = 150)
    private String reason;

    @Lob
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    public Long getId() {
        return id;
    }

    public ModeratedContent getContent() {
        return content;
    }

    public void setContent(ModeratedContent content) {
        this.content = content;
    }

    public AdminAlertType getType() {
        return type;
    }

    public void setType(AdminAlertType type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }
}
