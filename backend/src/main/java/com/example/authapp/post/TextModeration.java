package com.example.authapp.post;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "text_moderation")
public class TextModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "dominant_sentiment", length = 32)
    private String dominantSentiment;

    @Column(name = "negative_score")
    private double negativeScore;

    @Column(name = "pii_detected", nullable = false)
    private boolean piiDetected;

    @Lob
    @Column(name = "pii_entities_json")
    private String piiEntitiesJson;

    @Lob
    @Column(name = "sentiment_scores_json")
    private String sentimentScoresJson;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public String getDominantSentiment() {
        return dominantSentiment;
    }

    public void setDominantSentiment(String dominantSentiment) {
        this.dominantSentiment = dominantSentiment;
    }

    public double getNegativeScore() {
        return negativeScore;
    }

    public void setNegativeScore(double negativeScore) {
        this.negativeScore = negativeScore;
    }

    public boolean isPiiDetected() {
        return piiDetected;
    }

    public void setPiiDetected(boolean piiDetected) {
        this.piiDetected = piiDetected;
    }

    public String getPiiEntitiesJson() {
        return piiEntitiesJson;
    }

    public void setPiiEntitiesJson(String piiEntitiesJson) {
        this.piiEntitiesJson = piiEntitiesJson;
    }

    public String getSentimentScoresJson() {
        return sentimentScoresJson;
    }

    public void setSentimentScoresJson(String sentimentScoresJson) {
        this.sentimentScoresJson = sentimentScoresJson;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

