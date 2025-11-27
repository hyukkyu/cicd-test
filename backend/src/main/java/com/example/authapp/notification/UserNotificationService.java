package com.example.authapp.notification;

import com.example.authapp.user.User;
import com.example.authapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserNotificationService {

    private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);

    private final UserNotificationRepository userNotificationRepository;
    private final UserRepository userRepository;

    public UserNotificationService(UserNotificationRepository userNotificationRepository,
                                   UserRepository userRepository) {
        this.userNotificationRepository = userNotificationRepository;
        this.userRepository = userRepository;
    }

    public UserNotificationDto notifyDisciplinaryAction(User user,
                                                       String message,
                                                       String link,
                                                       boolean suspended,
                                                       boolean blocked,
                                                       String blockedReason) {
        UserNotificationDto dto = createNotification(
                user,
                UserNotificationType.DISCIPLINARY_ACTION,
                message,
                link,
                blocked,
                blockedReason
        );

        if (suspended) {
            notifyAccountSuspended(user);
        }

        return dto;
    }

    public UserNotificationDto notifyDisciplinaryAction(User user, String message, String link, boolean suspended) {
        return notifyDisciplinaryAction(user, message, link, suspended, false, null);
    }

    public UserNotificationDto notifyWarning(User user, String message, String link) {
        return createNotification(user, UserNotificationType.WARNING, message, link);
    }

    public UserNotificationDto notifyAccountSuspended(User user) {
        return createNotification(user, UserNotificationType.ACCOUNT_SUSPENDED, buildSuspensionMessage(user), "/login", false, null);
    }

    @Transactional(readOnly = true)
    public List<UserNotificationDto> getRecentNotifications(User user, int limit) {
        int pageSize = limit <= 0 ? 10 : Math.min(limit, 20);
        PageRequest pageRequest = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserNotification> page = userNotificationRepository.findByUser(user, pageRequest);
        return page.getContent().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countUnread(User user) {
        return userNotificationRepository.countByUserAndReadFalse(user);
    }

    public void markAsRead(User user, Long notificationId) {
        userNotificationRepository.findByIdAndUser(notificationId, user)
                .ifPresent(notification -> {
                    notification.markRead();
                    userNotificationRepository.save(notification);
                });
    }

    public void markAllAsRead(User user) {
        List<UserNotification> unread = userNotificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(notification -> !notification.isRead())
                .collect(Collectors.toList());
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(UserNotification::markRead);
        userNotificationRepository.saveAll(unread);
    }

    public void safeNotifyDisciplinaryAction(Long userId, String message, String link, boolean suspended) {
        try {
            userRepository.findById(userId).ifPresent(user -> notifyDisciplinaryAction(user, message, link, suspended));
        } catch (Exception ex) {
            log.error("Failed to push disciplinary notification to userId={}", userId, ex);
        }
    }
    public UserNotificationDto notifyContentAction(User user, String message, String link) {
        return createNotification(user, UserNotificationType.CONTENT_ACTION, message, link, false, null);
    }

    public UserNotificationDto notifyContentAction(User user, String message, String link, boolean blocked, String blockedReason) {
        return createNotification(user, UserNotificationType.CONTENT_ACTION, message, link, blocked, blockedReason);
    }

    public UserNotificationDto notifyReportResult(User user, String message, String link) {
        return createNotification(user, UserNotificationType.REPORT_RESULT, message, link, false, null);
    }

    public UserNotificationDto notifyReportSubmitted(User user, String message, String link) {
        return createNotification(user, UserNotificationType.REPORT_SUBMITTED, message, link, false, null);
    }

    public UserNotificationDto notifyAdminNote(User user, String message, String link) {
        return createNotification(user, UserNotificationType.ADMIN_NOTE, message, link, false, null);
    }

    private String buildSuspensionMessage(User user) {
        LocalDateTime suspendedAt = user.getSuspendedAt();
        String formatted = suspendedAt != null ? suspendedAt.toString() : LocalDateTime.now().toString();
        return "귀하의 계정이 " + formatted + "에 정지되었습니다. 자세한 내용은 관리자에게 문의해 주세요.";
    }

    private UserNotificationDto createNotification(User user, UserNotificationType type, String message, String link) {
        return createNotification(user, type, message, link, false, null);
    }

    private UserNotificationDto createNotification(User user,
                                                   UserNotificationType type,
                                                   String message,
                                                   String link,
                                                   boolean blocked,
                                                   String blockedReason) {
        if (user == null) {
            return null;
        }
        try {
            UserNotification notification = new UserNotification();
            notification.setUser(user);
            notification.setType(type);
            notification.setMessage(message);
            notification.setLink(link);
            notification.setRead(false);
             notification.setBlocked(blocked);
             notification.setBlockedReason(blocked ? sanitizeBlockedReason(blockedReason) : null);
            return toDto(userNotificationRepository.save(notification));
        } catch (Exception ex) {
            log.error("Failed to create notification type={} for userId={}", type, user.getId(), ex);
            return null;
        }
    }

    private UserNotificationDto toDto(UserNotification notification) {
        return new UserNotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getLink(),
                notification.isRead(),
                notification.isBlocked(),
                notification.getBlockedReason(),
                notification.getCreatedAt()
        );
    }

    private String sanitizeBlockedReason(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 250 ? trimmed.substring(0, 250) + "..." : trimmed;
    }
}
