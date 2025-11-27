package com.example.authapp.user;

import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.example.authapp.comment.CommentRepository;
import com.example.authapp.post.PostRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {

    private static final String COGNITO_PASSWORD_PLACEHOLDER = "COGNITO_MANAGED_PASSWORD";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CognitoService cognitoService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       CognitoService cognitoService,
                       PostRepository postRepository,
                       CommentRepository commentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.cognitoService = cognitoService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    public void signup(User user, String profilePictureUrl) {
        ensureUsernameAvailable(user.getUsername());
        ensureEmailAvailable(user.getEmail());

        Role requestedRole = Optional.ofNullable(user.getRole()).orElse(Role.USER);

        if (requestedRole == Role.ADMIN) {
            registerLocalAdmin(user);
        } else {
            registerCognitoUser(user);
        }

        user.setRole(requestedRole == Role.ADMIN ? Role.ADMIN : Role.USER);
        user.setWarnCount(0L);
        user.setProfilePictureUrl(profilePictureUrl);
        userRepository.save(user);
    }

    private void registerLocalAdmin(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
    }

    private void registerCognitoUser(User user) {
        cognitoService.signUp(user.getUsername(), user.getPassword(), user.getEmail(), user.getNickname());
        // Persist a hashed placeholder so DAO authentication always fails safely.
        user.setPassword(passwordEncoder.encode(COGNITO_PASSWORD_PLACEHOLDER));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    public void updateProfile(String username, ProfileEditForm profileEditForm) {
        updateProfile(username, profileEditForm, null);
    }

    public void updateProfile(String username, ProfileEditForm profileEditForm, String profilePictureUrl) {
        User user = findByUsername(username);

        if (user.getRole() != Role.ADMIN) {
            List<AttributeType> attributes = new ArrayList<>();
            attributes.add(new AttributeType().withName("nickname").withValue(profileEditForm.getNickname()));
            cognitoService.updateUserAttributes(user.getUsername(), attributes);
        }

        user.setNickname(profileEditForm.getNickname());
        if (profilePictureUrl != null) {
            user.setProfilePictureUrl(profilePictureUrl);
        }
        userRepository.save(user);
    }

    public boolean changePassword(String username, PasswordChangeForm form) {
        User user = findByUsername(username);

        if (isCognitoManaged(user)) {
            // Cognito password flows handled via CognitoService (see controller).
            return false;
        }

        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPassword())) {
            return false;
        }

        if (!form.getNewPassword().equals(form.getConfirmNewPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);
        return true;
    }

    @Transactional
    public void deleteUser(String username) {
        User user = findByUsername(username);

        if (user.getRole() == Role.ADMIN) {
            markAdminAsDeleted(user);
            return;
        }

        postRepository.findByVoterContains(user).forEach(post -> {
            post.getVoter().remove(user);
            postRepository.save(post);
        });

        postRepository.deleteByAuthor(user);
        commentRepository.deleteByAuthor(user);

        cognitoService.adminDeleteUser(user.getUsername());
        userRepository.delete(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    public boolean isUsernameAvailable(String username) {
        return userRepository.findByUsername(username).isEmpty();
    }

    private void ensureUsernameAvailable(String username) {
        if (!isUsernameAvailable(username)) {
            throw new DuplicateUsernameException("Username already taken: " + username);
        }
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException("Email already registered: " + email);
        }
    }

    private boolean isCognitoManaged(User user) {
        return user.getRole() != Role.ADMIN
                && passwordEncoder.matches(COGNITO_PASSWORD_PLACEHOLDER, user.getPassword());
    }

    private void markAdminAsDeleted(User user) {
        user.setRole(Role.DELETED);
        user.setNickname("탈퇴한 사용자");
        user.setEmail("deleted_" + user.getId() + "@example.com");
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(UserStatus.DELETED);
        user.setEnabled(false);
        userRepository.save(user);
    }

    public User enforceLoginRestrictions(User user) {
        if (user == null) {
            return null;
        }

        if (user.getWarnCount() != null && user.getWarnCount() > 2 && user.isEnabled()) {
            user.block();
            userRepository.save(user);
            try {
                cognitoService.adminDisableUser(user.getUsername());
            } catch (Exception e) {
                // 로그인 차단은 DB 상태로 우선 보장, Cognito 동기화 실패는 로그만 남김
                org.slf4j.LoggerFactory.getLogger(UserService.class)
                        .warn("Failed to disable Cognito user {} after warn threshold: {}", user.getUsername(), e.getMessage());
            }
        }

        if (!user.isEnabled()) {
            throw new DisabledException("User account is disabled.");
        }

        return user;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        enforceLoginRestrictions(user);

        if (user.getRole() == Role.DELETED || user.getStatus() == UserStatus.DELETED) {
            throw new DisabledException("User with username " + username + " has been deleted.");
        }

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                Collections.singleton(authority)
        );
    }
}
