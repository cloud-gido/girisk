package com.girisk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.girisk.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> writeJson(
                                request, response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                writeJson(request, response, HttpServletResponse.SC_FORBIDDEN, "无权访问该资源");
                            } else {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                            }
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/stream/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.GET, "/login", "/girisk/**", "/sports/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .headers(h -> h.frameOptions(f -> f.disable()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(message));
    }
}
