package com.example.authapp.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByReadFalse();

    List<Notification> findTop10ByOrderByCreatedAtDesc();

    List<Notification> findByReadFalseOrderByCreatedAtDesc();

    List<Notification> findByTypeAndReadFalseOrderByCreatedAtDesc(NotificationType type);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
