package com.example.authapp.notification;

import com.example.authapp.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findTop20ByUserOrderByCreatedAtDesc(User user);
    Page<UserNotification> findByUser(User user, Pageable pageable);
    long countByUserAndReadFalse(User user);
    Optional<UserNotification> findByIdAndUser(Long id, User user);
    List<UserNotification> findByUserOrderByCreatedAtDesc(User user);
}
