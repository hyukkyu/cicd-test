package com.example.authapp.user;

import com.example.authapp.user.dto.FindIdRequest;
import com.example.authapp.user.dto.FindIdResponse;
import com.example.authapp.user.dto.LoginRequest;
import com.example.authapp.user.dto.LoginResponse;
import com.example.authapp.user.dto.PasswordChangeRequest;
import com.example.authapp.user.dto.PasswordResetCodeRequest;
import com.example.authapp.user.dto.PasswordResetRequest;
import com.example.authapp.user.dto.PasswordResetVerifyRequest;
import com.example.authapp.user.dto.ProfileResponse;
import com.example.authapp.user.dto.ResendVerificationRequest;
import com.example.authapp.user.dto.SimpleMessageResponse;
import com.example.authapp.user.dto.UsernameAvailabilityResponse;
import com.example.authapp.user.dto.VerifyEmailRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;

import com.example.authapp.s3.S3Service;
import com.example.authapp.user.dto.SignupResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthRestController {

    private static final Logger log = LoggerFactory.getLogger(AuthRestController.class);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final S3Service s3Service;
    private final CognitoService cognitoService;

    public AuthRestController(AuthenticationManager authenticationManager,
                              UserService userService,
                              S3Service s3Service,
                              CognitoService cognitoService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.s3Service = s3Service;
        this.cognitoService = cognitoService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken(request.username(), request.password());
        Authentication authentication = authenticationManager.authenticate(token);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        User user = userService.findByUsername(request.username());
        return LoginResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponse> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        return ResponseEntity.ok(LoginResponse.from(user));
    }

    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SignupResponse signup(@RequestParam("username") String username,
                                 @RequestParam("password") String password,
                                 @RequestParam("nickname") String nickname,
                                 @RequestParam("email") String email,
                                 @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture) throws IOException {
        String profileUrl = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            profileUrl = s3Service.upload(profilePicture, "profile-pictures");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname);
        user.setEmail(email);
        try {
            userService.signup(user, profileUrl);
        } catch (DuplicateUsernameException | DuplicateEmailException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
        return new SignupResponse("회원가입이 완료되었습니다. 이메일 인증을 진행해 주세요.");
    }

    @GetMapping("/check-username")
    public UsernameAvailabilityResponse checkUsername(@RequestParam String username) {
        boolean available = userService.isUsernameAvailable(username);
        return new UsernameAvailabilityResponse(available);
    }

    @GetMapping("/profile")
    public ProfileResponse profile() {
        User user = requireAuthenticatedUser();
        return ProfileResponse.from(user);
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse updateProfile(@RequestParam("nickname") String nickname,
                                         @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture) throws IOException {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임을 입력하세요.");
        }
        User user = requireAuthenticatedUser();
        ProfileEditForm form = new ProfileEditForm();
        form.setNickname(nickname.trim());

        if (profilePicture != null && !profilePicture.isEmpty()) {
            String profileUrl = s3Service.upload(profilePicture, "profile-pictures");
            userService.updateProfile(user.getUsername(), form, profileUrl);
        } else {
            userService.updateProfile(user.getUsername(), form);
        }
        User updated = userService.findByUsername(user.getUsername());
        return ProfileResponse.from(updated);
    }

    @PostMapping("/password/change")
    public SimpleMessageResponse changePassword(@Validated @RequestBody PasswordChangeRequest request) {
        User user = requireAuthenticatedUser();
        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cognito 계정은 비밀번호 찾기 기능을 이용해 주세요.");
        }

        PasswordChangeForm form = new PasswordChangeForm();
        form.setCurrentPassword(request.currentPassword());
        form.setNewPassword(request.newPassword());
        form.setConfirmNewPassword(request.confirmNewPassword());

        boolean changed = userService.changePassword(user.getUsername(), form);
        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호를 다시 확인해주세요.");
        }
        return new SimpleMessageResponse("비밀번호가 변경되었습니다.");
    }

    @DeleteMapping("/me")
    public SimpleMessageResponse deleteAccount(HttpServletRequest request, HttpServletResponse response) {
        User user = requireAuthenticatedUser();
        userService.deleteUser(user.getUsername());
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return new SimpleMessageResponse("회원 탈퇴가 완료되었습니다.");
    }

    @PostMapping("/verify-email")
    public SimpleMessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        User user = loadUser(request.username());
        try {
            cognitoService.confirmSignUp(user.getUsername(), request.code());
            boolean groupAdded = true;
            try {
                cognitoService.addUserToGroup(user.getUsername(), "user");
            } catch (RuntimeException ex) {
                groupAdded = false;
                log.warn("Email verified but failed to add user {} to Cognito group 'user': {}", user.getUsername(), ex.getMessage());
            }
            String message = groupAdded
                    ? "이메일 인증이 완료되었습니다."
                    : "이메일 인증은 완료되었지만 그룹 배정에 실패했습니다. 관리자에게 문의하세요.";
            return new SimpleMessageResponse(message);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/resend-verification-code")
    public SimpleMessageResponse resendVerificationCode(@Valid @RequestBody ResendVerificationRequest request) {
        User user = loadUser(request.username());
        try {
            cognitoService.resendConfirmationCode(user.getUsername());
            return new SimpleMessageResponse("인증 코드를 다시 전송했습니다.");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/find-id")
    public FindIdResponse findId(@Valid @RequestBody FindIdRequest request) {
        try {
            User user = userService.findByEmail(request.email());
            return new FindIdResponse(user.getUsername());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 이메일로 등록된 아이디가 없습니다.", ex);
        }
    }

    @PostMapping("/find-password/request-code")
    public SimpleMessageResponse requestResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        User user = loadUser(request.username());
        if (!user.getEmail().equalsIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입력하신 이메일이 계정 정보와 일치하지 않습니다.");
        }
        try {
            cognitoService.forgotPassword(user.getUsername());
            return new SimpleMessageResponse("인증 코드가 이메일로 전송되었습니다.");
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/find-password/verify-code")
    public SimpleMessageResponse verifyResetCode(@Valid @RequestBody PasswordResetVerifyRequest request) {
        loadUser(request.username());
        // Cognito는 confirmForgotPassword 호출 시 인증 코드를 검증하므로 여기서는 성공 응답만 반환합니다.
        return new SimpleMessageResponse("인증 코드를 확인했습니다.");
    }

    @PostMapping("/find-password/reset")
    public SimpleMessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "새 비밀번호가 일치하지 않습니다.");
        }
        User user = loadUser(request.username());
        try {
            cognitoService.confirmForgotPassword(user.getUsername(), request.verificationCode(), request.newPassword());
            return new SimpleMessageResponse("비밀번호가 재설정되었습니다.");
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private User loadUser(String username) {
        try {
            return userService.findByUsername(username);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다.", ex);
        }
    }

    private User requireAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userService.findByUsername(authentication.getName());
    }
}
