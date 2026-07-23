package com.girisk.config;

import com.girisk.auth.JwtTokenProvider;
import com.girisk.auth.rbac.RbacService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RbacService rbacService;
    private final String internalApiKey;

    public JwtAuthFilter(
            JwtTokenProvider jwtTokenProvider,
            RbacService rbacService,
            @Value("${girisk.internal-api-key}") String internalApiKey) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.rbacService = rbacService;
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String internalKey = request.getHeader("X-Internal-Key");
        if (internalApiKey.equals(internalKey)) {
            var authorities = rbacService.systemAuthorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("system", null, authorities));
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = jwtTokenProvider.parse(header.substring(7));
                Set<String> authorityNames = new LinkedHashSet<>();
                for (String role : jwtTokenProvider.rolesFromClaims(claims)) {
                    authorityNames.add("ROLE_" + role);
                }
                authorityNames.addAll(jwtTokenProvider.permsFromClaims(claims));
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                for (String name : authorityNames) {
                    authorities.add(new SimpleGrantedAuthority(name));
                }
                var auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
