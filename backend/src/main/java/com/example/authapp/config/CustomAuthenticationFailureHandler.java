package com.example.authapp.config;

import com.example.authapp.user.User;
import com.example.authapp.user.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final UserRepository userRepository;

    public CustomAuthenticationFailureHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username");
        String message = "사용자ID 또는 비밀번호를 확인해 주세요.";

        if (exception instanceof DisabledException && username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                if (user.getSuspendedAt() != null) {
                    String formatted = user.getSuspendedAt().format(FORMATTER);
                    message = "계정이 정지되었습니다. 정지 일시: " + formatted + ". 관리자에게 문의해 주세요.";
                } else {
                    message = "계정이 정지된 상태입니다. 관리자에게 문의해 주세요.";
                }
            }
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("LOGIN_ERROR_MESSAGE", message);
        super.onAuthenticationFailure(request, response, exception);
    }
}
