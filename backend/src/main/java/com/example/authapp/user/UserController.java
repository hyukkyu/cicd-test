package com.example.authapp.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.security.Principal;

import com.example.authapp.post.PostService;
import com.example.authapp.s3.S3Service;
import com.example.authapp.user.CognitoService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import com.example.authapp.user.DuplicateEmailException; // Import the custom exception
import org.springframework.web.bind.annotation.ResponseBody; // Import for @ResponseBody
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final PostService postService;
    private final S3Service s3Service;
    private final CognitoService cognitoService;

    @Autowired
    public UserController(UserService userService, PostService postService, S3Service s3Service, CognitoService cognitoService) {
        this.userService = userService;
        this.postService = postService;
        this.s3Service = s3Service;
        this.cognitoService = cognitoService;
    }

    @PostMapping("/signup")
    public String signup(UserForm userForm, @RequestParam("profilePicture") MultipartFile profilePicture, RedirectAttributes redirectAttributes, HttpSession session) throws IOException {
        String profilePictureUrl = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            profilePictureUrl = s3Service.upload(profilePicture, "profile-pictures");
        }

        User user = new User();
        user.setUsername(userForm.getUsername());
        user.setPassword(userForm.getPassword()); // Password is used for Cognito signup, but not stored locally
        user.setNickname(userForm.getNickname());
        user.setEmail(userForm.getEmail());
        try {
            userService.signup(user, profilePictureUrl);
            // After successful signup, redirect to email verification page
            session.setAttribute("signupUsername", user.getUsername()); // Store in session
            return "redirect:/verify-email";
        } catch (DuplicateUsernameException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 사용하고 있는 사용자 ID입니다.");
            return "redirect:/signup";
        } catch (DuplicateEmailException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 사용하고 있는 이메일입니다.");
            return "redirect:/signup";
        }
    }

    @GetMapping("/signup")
    public String showSignupPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        model.addAttribute("userForm", new UserForm());
        return "signup";
    }

    @GetMapping("/")
    public String showRootPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }

        model.addAttribute("recentPosts", this.postService.getRecentList());
        model.addAttribute("popularGalleryPosts", this.postService.getPopularGalleryPosts(10)); // Get top 10

        // Prepare data for sub-board dropdowns
        java.util.Map<String, java.util.List<String>> subBoardItems = new java.util.HashMap<>();
        java.util.List<String> mainBoardNames = java.util.Arrays.asList("game", "exercise", "movie", "music", "travel", "invest");
        for (String mainBoardName : mainBoardNames) {
            subBoardItems.put(mainBoardName, postService.getUniqueSubBoardNamesForMainBoard(mainBoardName));
        }
        model.addAttribute("subBoardItems", subBoardItems);
        model.addAttribute("mainBoardNames", mainBoardNames); // Pass mainBoardNames to the model

        // Prepare data for main board display names (English to Korean)
        java.util.Map<String, String> mainBoardDisplayNames = new java.util.HashMap<>();
        mainBoardDisplayNames.put("game", "게임");
        mainBoardDisplayNames.put("exercise", "운동");
        mainBoardDisplayNames.put("movie", "영화");
        mainBoardDisplayNames.put("music", "음악");
        mainBoardDisplayNames.put("travel", "여행");
        mainBoardDisplayNames.put("invest", "투자");
        model.addAttribute("mainBoardDisplayNames", mainBoardDisplayNames);

        return "home";
    }

    @GetMapping("/login")
    public String showLoginPage(Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object message = session.getAttribute("LOGIN_ERROR_MESSAGE");
            if (message != null) {
                model.addAttribute("loginErrorMessage", String.valueOf(message));
                session.removeAttribute("LOGIN_ERROR_MESSAGE");
            }
        }
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String showHomePage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }

        model.addAttribute("recentPosts", this.postService.getRecentList());
        model.addAttribute("popularGalleryPosts", this.postService.getPopularGalleryPosts(10)); // Get top 10

        // Prepare data for sub-board dropdowns
        java.util.Map<String, java.util.List<String>> subBoardItems = new java.util.HashMap<>();
        java.util.List<String> mainBoardNames = java.util.Arrays.asList("game", "exercise", "movie", "music", "travel", "invest");
        for (String mainBoardName : mainBoardNames) {
            subBoardItems.put(mainBoardName, postService.getUniqueSubBoardNamesForMainBoard(mainBoardName));
        }
        model.addAttribute("subBoardItems", subBoardItems);
        model.addAttribute("mainBoardNames", mainBoardNames); // Pass mainBoardNames to the model

        // Prepare data for main board display names (English to Korean)
        java.util.Map<String, String> mainBoardDisplayNames = new java.util.HashMap<>();
        mainBoardDisplayNames.put("game", "게임");
        mainBoardDisplayNames.put("exercise", "운동");
        mainBoardDisplayNames.put("movie", "영화");
        mainBoardDisplayNames.put("music", "음악");
        mainBoardDisplayNames.put("travel", "여행");
        mainBoardDisplayNames.put("invest", "투자");
        model.addAttribute("mainBoardDisplayNames", mainBoardDisplayNames);

        return "home";
    }

    @GetMapping("/gallery")
    public String showGalleryPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        return "gallery";
    }

    @GetMapping("/freeboard")
    public String showBoardPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        return "freeboard";
    }

    @GetMapping("/my-info")
    public String showMyInfoPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
            java.util.List<com.example.authapp.post.Post> userPosts = postService.findByAuthor(currentUser);
            model.addAttribute("userPosts", userPosts);
            return "my-info";
        } else {
            // Handle case where user is not logged in or session expired
            return "redirect:/login";
        }
    }

    @GetMapping("/edit-profile")
    public String showEditProfilePage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = userService.findByUsername(principal.getName());
            ProfileEditForm profileEditForm = new ProfileEditForm();
            profileEditForm.setNickname(currentUser.getNickname());
            model.addAttribute("profileEditForm", profileEditForm);
            model.addAttribute("currentUser", currentUser);
            return "edit-profile";
        } else {
            return "redirect:/login";
        }
    }

    @PostMapping("/edit-profile")
    public String updateProfile(@ModelAttribute("profileEditForm") ProfileEditForm profileEditForm,
                                @RequestParam("profilePicture") MultipartFile profilePicture,
                                Principal principal,
                                HttpSession session) throws IOException {
        if (principal != null) {
            String username = principal.getName();
            String profilePictureUrl = null;
            if (profilePicture != null && !profilePicture.isEmpty()) {
                profilePictureUrl = s3Service.upload(profilePicture, "profile-pictures");
            }
            userService.updateProfile(username, profileEditForm, profilePictureUrl);
            // Update the user object in the session
            User updatedUser = userService.findByUsername(username);
            session.setAttribute("user", updatedUser);
            session.setAttribute("CURRENT_USER_CACHE", updatedUser);
        } else {
            return "redirect:/login";
        }
        return "redirect:/my-info";
    }

    @GetMapping("/change-password")
    public String showChangePasswordPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@ModelAttribute("passwordChangeForm") PasswordChangeForm form, Principal principal, RedirectAttributes redirectAttributes, HttpSession session) {
        if (principal != null) {
            String username = principal.getName();
            // TODO: Implement password change with Cognito
            redirectAttributes.addFlashAttribute("errorMessage", "비밀번호 변경 기능은 현재 Cognito 통합 중입니다.");
        } else {
            return "redirect:/login";
        }
        return "redirect:/my-info";
    }

    @GetMapping("/confirm-delete")
    public String showConfirmDeletePage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        return "confirm-delete";
    }

    @PostMapping("/delete-account")
    public String deleteAccount(Principal principal, HttpSession session, RedirectAttributes redirectAttributes, HttpServletRequest request, HttpServletResponse response) {
        if (principal != null) {
            try {
                String username = principal.getName();
                userService.deleteUser(username);
                session.invalidate();
                new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
                redirectAttributes.addFlashAttribute("successMessage", "회원 탈퇴가 완료되었습니다.");
                return "redirect:/";
            } catch (Exception e) {
                e.printStackTrace();
                redirectAttributes.addFlashAttribute("errorMessage", "회원 탈퇴 중 오류가 발생했습니다.");
                return "redirect:/my-info";
            }
        } else {
            return "redirect:/login";
        }
    }

    @GetMapping("/verify-email")
    public String showVerifyEmailPage(HttpSession session,
                                      @RequestParam(required = false) String message,
                                      @RequestParam(required = false) String error,
                                      Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        String username = (String) session.getAttribute("signupUsername");
        if (username == null) {
            // If username is not in session, redirect to signup or an error page
            return "redirect:/signup";
        }
        model.addAttribute("username", username);
        
        if (message != null) {
            model.addAttribute("message", message);
        }
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "verify-email";
    }

    @PostMapping("/verify-email")
    public String verifyEmail(HttpSession session,
                              @RequestParam String verificationCode,
                              RedirectAttributes redirectAttributes) {
        String username = (String) session.getAttribute("signupUsername");
        if (username == null) {
            // If username is not in session, redirect to signup or an error page
            return "redirect:/signup";
        }
        try {
            String cognitoUsername = resolveCognitoUsername(username);
            cognitoService.confirmSignUp(cognitoUsername, verificationCode);
            session.removeAttribute("signupUsername"); // Remove username from session after successful verification
            redirectAttributes.addFlashAttribute("message", "이메일 인증이 완료되었습니다. 로그인해주세요.");
            return "redirect:/login";
        } catch (Exception e) {
            // Don't add username to redirectAttributes as it's not in URL anymore
            redirectAttributes.addFlashAttribute("error", "인증 코드가 올바르지 않습니다: " + e.getMessage());
            return "redirect:/verify-email";
        }
    }


    @GetMapping("/resend-verification-code")
    public String resendVerificationCode(HttpSession session,
                                         RedirectAttributes redirectAttributes, Principal principal, Model model) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        String username = (String) session.getAttribute("signupUsername");
        if (username == null) {
            return "redirect:/signup";
        }
        try {
            String cognitoUsername = resolveCognitoUsername(username);
            cognitoService.resendConfirmationCode(cognitoUsername);
            redirectAttributes.addFlashAttribute("message", "인증 코드를 다시 전송했습니다. 이메일을 확인해주세요.");
            return "redirect:/verify-email";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "인증 코드 재전송에 실패했습니다: " + e.getMessage());
            return "redirect:/verify-email";
        }
    }

    @GetMapping("/check-username")
    @ResponseBody
    public boolean checkUsernameAvailability(@RequestParam String username) {
        return userService.isUsernameAvailable(username);
    }

    @GetMapping("/find-id")
    public String showFindIdPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        return "find-id";
    }

    @PostMapping("/find-id")
    public String findId(@RequestParam String email, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findByEmail(email);
            redirectAttributes.addFlashAttribute("foundUsername", user.getUsername());
            return "redirect:/find-id-result";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "아이디 찾기 중 오류가 발생했습니다: " + e.getMessage());
            return "redirect:/find-id";
        }
    }

    @GetMapping("/find-id-result")
    public String showFindIdResultPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        return "find-id-result";
    }

    @GetMapping("/find-password")
    public String showFindPasswordPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        return "find-password";
    }

    @PostMapping("/find-password/request-code")
    public String requestPasswordResetCode(@RequestParam String username, @RequestParam String email, Model model, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findByUsername(username);
            if (!user.getEmail().equalsIgnoreCase(email)) {
                model.addAttribute("errorMessage", "입력하신 이메일이 계정 정보와 일치하지 않습니다.");
                return "find-password";
            }
            cognitoService.forgotPassword(user.getUsername());
            model.addAttribute("codeSent", true);
            model.addAttribute("username", username);
            model.addAttribute("successMessage", "인증 코드가 이메일로 전송되었습니다.");
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", "비밀번호 재설정 코드 요청 중 오류가 발생했습니다: " + e.getMessage());
        }
        return "find-password";
    }

    @PostMapping("/find-password/verify-code")
    public String verifyCode(@RequestParam String username, @RequestParam String verificationCode, RedirectAttributes redirectAttributes) {
        // In a real implementation, you would have a proper verification logic here.
        // For now, we'll simulate a successful verification.
        boolean isCodeValid = true; // Replace with actual call to cognitoService.verifyForgotPasswordCode(username, verificationCode)

        if (isCodeValid) {
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("verificationCode", verificationCode);
            return "redirect:/reset-password";
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "인증 코드가 올바르지 않습니다.");
            redirectAttributes.addFlashAttribute("codeSent", true);
            redirectAttributes.addFlashAttribute("username", username);
            return "redirect:/find-password";
        }
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(Model model, Principal principal) {
        if (principal != null) {
            User currentUser = this.userService.findByUsername(principal.getName());
            model.addAttribute("currentUser", currentUser);
        }
        // The username and verificationCode are passed as flash attributes
        // and will be automatically added to the model.
        return "reset-password";
    }

    @PostMapping("/find-password/reset")
    public String resetPassword(@RequestParam String username, @RequestParam String verificationCode, @RequestParam String newPassword, @RequestParam String confirmPassword, RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "새 비밀번호가 일치하지 않습니다.");
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("verificationCode", verificationCode);
            return "redirect:/reset-password";
        }
        try {
            User user = userService.findByUsername(username);
            cognitoService.confirmForgotPassword(user.getUsername(), verificationCode, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "비밀번호가 성공적으로 재설정되었습니다. 로그인해주세요.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "비밀번호 재설정 중 오류가 발생했습니다: " + e.getMessage());
            redirectAttributes.addFlashAttribute("username", username);
            redirectAttributes.addFlashAttribute("verificationCode", verificationCode);
            return "redirect:/reset-password";
        }
    }

    private String resolveCognitoUsername(String username) {
        return userService.findByUsername(username).getUsername();
    }
}
