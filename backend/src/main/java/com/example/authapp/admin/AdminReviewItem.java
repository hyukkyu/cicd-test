package com.example.authapp.admin;

import com.example.authapp.comment.Comment;
import com.example.authapp.post.Post;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class AdminReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @Lob
    private String moderatedText;

    private String contentUrl; // URL of the image/video

    private String contentType; // IMAGE or VIDEO

    @Lob
    private String moderationResult; // Full JSON result from Rekognition

    private boolean inappropriateDetected; // True if Rekognition found issues

    @Enumerated(EnumType.STRING)
    private ReviewStatus reviewStatus; // PENDING, APPROVED, REJECTED

    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.reviewStatus == null) {
            this.reviewStatus = ReviewStatus.PENDING;
        }
    }

    public enum ReviewStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
