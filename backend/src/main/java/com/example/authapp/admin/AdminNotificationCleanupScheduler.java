package com.example.authapp.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AdminNotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationCleanupScheduler.class);
    private static final int RETENTION_DAYS = 7;

    private final NotificationRepository notificationRepository;

    public AdminNotificationCleanupScheduler(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void purgeExpiredNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        long removed = notificationRepository.deleteByCreatedAtBefore(cutoff);
        if (removed > 0) {
            log.info("Deleted {} admin notifications older than {} days (cutoff={}).", removed, RETENTION_DAYS, cutoff);
        }
    }
}
