package com.example.authapp.user;

import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Convert(converter = UserStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "warn_count", nullable = false)
    private Long warnCount;

    @Column(name = "last_sanction_at")
    private LocalDateTime lastSanctionAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (warnCount == null) {
            warnCount = 0L;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getWarnCount() {
        return warnCount;
    }

    public void setWarnCount(Long warnCount) {
        this.warnCount = warnCount;
    }

    public void incrementWarnCount() {
        if (warnCount == null) {
            warnCount = 0L;
        }
        warnCount += 1;
        this.lastSanctionAt = LocalDateTime.now();
    }

    public LocalDateTime getLastSanctionAt() {
        return lastSanctionAt;
    }

    public void setLastSanctionAt(LocalDateTime lastSanctionAt) {
        this.lastSanctionAt = lastSanctionAt;
    }

    public LocalDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public void setSuspendedAt(LocalDateTime suspendedAt) {
        this.suspendedAt = suspendedAt;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.status = enabled ? UserStatus.ACTIVE : UserStatus.BLOCKED;
        if (enabled) {
            this.suspendedAt = null;
        } else if (this.suspendedAt == null) {
            this.suspendedAt = LocalDateTime.now();
        }
    }

    public void block() {
        this.suspendedAt = LocalDateTime.now();
        setEnabled(false);
    }

    public void unblock() {
        setEnabled(true);
        this.suspendedAt = null;
    }
}
