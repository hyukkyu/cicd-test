package com.example.authapp.user;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public class NewUserForm {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private List<MultipartFile> profilePictures;

    // Getters and Setters
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

    public List<MultipartFile> getProfilePictures() {
        return profilePictures;
    }

    public void setProfilePictures(List<MultipartFile> profilePictures) {
        this.profilePictures = profilePictures;
    }
}
