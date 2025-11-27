package com.example.authapp.video;

import com.example.authapp.post.Post;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class VideoModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(unique = true)
    private String jobId;

    @Column(name = "s3_object_key", length = 512)
    private String s3ObjectKey;

    private String status; // e.g., PENDING, SUCCEEDED, FAILED

    @Lob
    private String moderationResult; // Store raw JSON from Rekognition

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
