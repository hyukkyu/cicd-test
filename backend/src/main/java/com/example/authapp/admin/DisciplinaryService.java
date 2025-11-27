package com.example.authapp.admin;

import com.example.authapp.notification.UserNotificationService;
import com.example.authapp.user.CognitoService;
import com.example.authapp.user.User;
import com.example.authapp.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DisciplinaryService {

    private static final Logger log = LoggerFactory.getLogger(DisciplinaryService.class);

    private final UserRepository userRepository;
    private final UserNotificationService userNotificationService;
    private final CognitoService cognitoService;

    public DisciplinaryService(UserRepository userRepository,
                               UserNotificationService userNotificationService,
                               CognitoService cognitoService) {
        this.userRepository = userRepository;
        this.userNotificationService = userNotificationService;
        this.cognitoService = cognitoService;
    }

    public DisciplinaryResult applyWarning(User user, String message, String link) {
        return applyWarning(user, message, link, false, null);
    }

    public DisciplinaryResult applyWarning(User user, String message, String link, boolean blocked, String blockedReason) {
        user.incrementWarnCount();
        boolean suspended = false;

        if (user.getWarnCount() != null && user.getWarnCount() > 2) {
            user.block();
            user.setSuspendedAt(LocalDateTime.now());
            suspended = true;
            try {
                cognitoService.adminDisableUser(user.getUsername());
            } catch (Exception e) {
                log.warn("Failed to disable Cognito user {} after warnings: {}", user.getUsername(), e.getMessage());
            }
        }

        userRepository.save(user);
        userNotificationService.notifyDisciplinaryAction(user, message, link, suspended, blocked, blockedReason);
        return new DisciplinaryResult(user.getWarnCount(), suspended, user.getSuspendedAt());
    }

    public DisciplinaryResult applyWarning(Long userId, String message, String link) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return applyWarning(user, message, link, false, null);
    }

    public void notifyContentAction(User user, String message, String link) {
        notifyContentAction(user, message, link, false, null);
    }

    public void notifyContentAction(User user, String message, String link, boolean blocked, String blockedReason) {
        user.setLastSanctionAt(LocalDateTime.now());
        userRepository.save(user);
        userNotificationService.notifyContentAction(user, message, link, blocked, blockedReason);
    }

    public void notifySuspension(User user, String message, String link) {
        user.setLastSanctionAt(LocalDateTime.now());
        userRepository.save(user);
        userNotificationService.notifyDisciplinaryAction(user, message, link, true);
    }

    public record DisciplinaryResult(long warnCount, boolean suspended, LocalDateTime suspendedAt) {
    }
}
