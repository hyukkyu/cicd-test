package com.example.authapp.image;

import com.example.authapp.post.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageModerationRepository extends JpaRepository<ImageModeration, Long> {
    Optional<ImageModeration> findFirstByPostAndImageUrl(Post post, String imageUrl);
}
