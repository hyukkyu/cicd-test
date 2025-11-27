package com.example.authapp.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoModerationRepository extends JpaRepository<VideoModeration, Long> {
    Optional<VideoModeration> findByJobId(String jobId);

    Optional<VideoModeration> findByS3ObjectKey(String s3ObjectKey);
}
