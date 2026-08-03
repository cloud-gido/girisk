package com.girisk.config;

import com.girisk.auth.JwtTokenProvider;
import com.girisk.auth.TokenRevocationStore;
import com.girisk.auth.rbac.RbacService;
import io.jsonwebtoken.Claims;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RbacService rbacService;
    private final TokenRevocationStore revocationStore;
    private final String internalApiKey;

    public JwtAuthFilter(
            JwtTokenProvider jwtTokenProvider,
            RbacService rbacService,
            TokenRevocationStore revocationStore,
            @Value("${girisk.internal-api-key}") String internalApiKey) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.rbacService = rbacService;
        this.revocationStore = revocationStore;
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
                Claims claims = jwtTokenProvider.parse(header.substring(7));
                Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
                if (revocationStore.isRevoked(claims.getId(), claims.getSubject(), issuedAt)) {
                    SecurityContextHolder.clearContext();
                    chain.doFilter(request, response);
                    return;
                }
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
