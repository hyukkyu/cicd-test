package com.example.authapp.config;

import com.example.authapp.user.CognitoService;
import com.example.authapp.user.User;
import com.example.authapp.user.UserService;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.AuthenticationResultType;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class CognitoAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(CognitoAuthenticationProvider.class);

    private final CognitoService cognitoService;
    private final UserService userService;

    public CognitoAuthenticationProvider(CognitoService cognitoService, UserService userService) {
        this.cognitoService = cognitoService;
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        logger.info("Attempting to authenticate user");
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        User localUser;
        try {
            localUser = userService.findByUsername(username);
        } catch (RuntimeException ex) {
            logger.warn("User '{}' not found during authentication", username);
            throw new BadCredentialsException("Invalid username or password.");
        }

        try {
            logger.info("Authenticating user '{}' against Cognito", username);
            AdminInitiateAuthResult authResult = cognitoService.signIn(localUser.getUsername(), password);
            AuthenticationResultType tokens = authResult.getAuthenticationResult();
            logger.info("User '{}' successfully authenticated against Cognito", username);

            userService.enforceLoginRestrictions(localUser);
            logger.info("User '{}' found in local database and passed login restrictions", username);

            List<String> groups = cognitoService.listGroups(localUser.getUsername());
            boolean isCognitoAdmin = groups.stream().anyMatch(g -> "admin".equalsIgnoreCase(g));

            boolean isDbAdmin = localUser.getRole() == com.example.authapp.user.Role.ADMIN;
            if (isCognitoAdmin && !isDbAdmin) {
                logger.warn("User '{}' is admin in Cognito but not in DB. Blocking login for safety.", username);
                throw new AccessDeniedException("관리자 권한 불일치로 로그인할 수 없습니다.");
            }
            if (!isCognitoAdmin && isDbAdmin) {
                logger.warn("User '{}' is admin in DB but missing admin group in Cognito. Downgrading to user role.", username);
            }

            String effectiveRole = isCognitoAdmin && isDbAdmin ? "ADMIN" : "USER";

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + effectiveRole));
            org.springframework.security.core.userdetails.User springUser =
                new org.springframework.security.core.userdetails.User(
                    localUser.getUsername(),
                    "",
                    authorities
                );

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                springUser,
                null,
                springUser.getAuthorities()
            );

            authenticationToken.setDetails(tokens);

            return authenticationToken;
        } catch (DisabledException ex) {
            logger.warn("Authentication blocked for disabled user '{}': {}", username, ex.getMessage());
            throw ex;
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (Exception e) {
            logger.error("Authentication failed for user '{}': {}", username, e.getMessage());
            throw new BadCredentialsException("Authentication failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
