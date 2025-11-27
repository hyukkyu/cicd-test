package com.example.authapp.config;

import com.amazonaws.services.cognitoidp.model.AuthenticationResultType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collection;

@Component
public class AdminAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        boolean isAdmin = authorities.stream()
                .anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"));

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("idToken");
            session.removeAttribute("accessToken");
            session.removeAttribute("refreshToken");
        }

        Object details = authentication.getDetails();
        if (!isAdmin && details instanceof AuthenticationResultType && session != null) {
            AuthenticationResultType tokens = (AuthenticationResultType) details;
            session.setAttribute("idToken", tokens.getIdToken());
            session.setAttribute("accessToken", tokens.getAccessToken());
            session.setAttribute("refreshToken", tokens.getRefreshToken());
        }

        if (isAdmin) {
            response.sendRedirect("/admin/dashboard");
        } else {
            response.sendRedirect("/"); // Default redirect for non-admin users
        }
    }
}
