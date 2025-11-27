package com.example.authapp.notification;

import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/api/notifications")
@PreAuthorize("isAuthenticated()")
public class UserNotificationController {

    private final UserNotificationService userNotificationService;
    private final UserService userService;

    public UserNotificationController(UserNotificationService userNotificationService,
                                      UserService userService) {
        this.userNotificationService = userNotificationService;
        this.userService = userService;
    }

    @GetMapping
    public List<UserNotificationDto> list(@RequestParam(value = "limit", defaultValue = "10") int limit,
                                          Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return userNotificationService.getRecentNotifications(user, limit);
    }

    @GetMapping("/count")
    public Map<String, Long> unreadCount(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return Map.of("count", userNotificationService.countUnread(user));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable("id") Long id, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        userNotificationService.markAsRead(user, id);
    }

    @PostMapping("/read-all")
    public void markAllRead(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        userNotificationService.markAllAsRead(user);
    }
}
