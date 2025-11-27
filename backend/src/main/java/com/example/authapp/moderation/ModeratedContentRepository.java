package com.example.authapp.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModeratedContentRepository extends JpaRepository<ModeratedContent, Long> {
    Optional<ModeratedContent> findByReferenceId(String referenceId);
}
