package com.example.authapp.config;

import com.example.authapp.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserService userService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
                                                       CognitoAuthenticationProvider cognitoAuthenticationProvider,
                                                       DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.authenticationProvider(cognitoAuthenticationProvider);
        builder.authenticationProvider(daoAuthenticationProvider);
        return builder.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationManager authenticationManager,
                                           AuthenticationSuccessHandler adminAuthenticationSuccessHandler,
                                           CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http.authenticationManager(authenticationManager)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeRequests(authorize -> authorize
                .antMatchers("/actuator/**").permitAll()
                .antMatchers(
                        "/",
                        "/home",
                        "/login",
                        "/signup",
                        "/verify-email",
                        "/resend-verification-code",
                        "/find-id",
                        "/find-id-result",
                        "/find-password",
                        "/reset-password",
                        "/check-username",
                        "/post/list",
                        "/post/detail/**",
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/icon/**",
                        "/notice/list",
                        "/gallery",
                        "/freeboard"
                ).permitAll()
                // Grafana 프록시는 Grafana 자체에서 익명 뷰어 권한을 관리하므로 별도 인증 없이 통과시켜 iframe 임베드가 가능하도록 허용
                .antMatchers("/api/admin/monitoring/grafana/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .antMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .antMatchers(HttpMethod.GET, "/api/home", "/api/posts/**", "/api/notices/**", "/api/gallery/**").permitAll()
                .antMatchers("/api/auth/**").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/**").authenticated()
                .antMatchers("/report/**", "/user/api/notifications/**").authenticated()
                .antMatchers(
                        "/post/create",
                        "/post/modify/**",
                        "/post/delete/**",
                        "/comment/**",
                        "/my-info/**",
                        "/edit-profile/**",
                        "/change-password/**",
                        "/post/vote/**",
                        "/user/api/notifications/**"
                ).authenticated()
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.ignoringAntMatchers(
                    "/actuator/**",
                    "/api/**",
                    "/admin/api/**",
                    "/report/**",
                    "/login",
                    "/signup"))
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(adminAuthenticationSuccessHandler)
                .failureHandler(customAuthenticationFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
            )
            .headers(headers -> headers.frameOptions().disable());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend.allowed-origins:*}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        if ("*".equals(allowedOrigins)) {
            configuration.addAllowedOriginPattern("*");
        } else {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isBlank()) {
                    configuration.addAllowedOrigin(trimmed);
                    configuration.addAllowedOriginPattern(trimmed);
                }
            }
        }
        // Always allow our known domains even if environment variables fall behind
        configuration.addAllowedOriginPattern("https://*.cms-community.com");
        configuration.addAllowedOriginPattern("https://*.cloudfront.net");
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Set-Cookie"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
