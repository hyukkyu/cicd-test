package com.example.authapp.admin;

import com.example.authapp.post.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminReviewItemRepository extends JpaRepository<AdminReviewItem, Long> {
    List<AdminReviewItem> findByReviewStatusOrderByCreatedAtDesc(AdminReviewItem.ReviewStatus status);
    long countByReviewStatus(AdminReviewItem.ReviewStatus reviewStatus);
    long countByInappropriateDetectedTrue();
    Optional<AdminReviewItem> findFirstByPostAndContentUrlAndReviewStatus(Post post, String contentUrl, AdminReviewItem.ReviewStatus reviewStatus);

    List<AdminReviewItem> findByPostAndReviewStatus(Post post, AdminReviewItem.ReviewStatus reviewStatus);

    List<AdminReviewItem> findByCommentAndReviewStatus(com.example.authapp.comment.Comment comment, AdminReviewItem.ReviewStatus reviewStatus);
}
