package com.girisk.config;

import com.girisk.gateway.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantHeaderFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;

    public TenantHeaderFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String op = request.getHeader("X-Operator-Id");
        if (op != null && !op.isBlank()) {
            tenantContext.setOperatorId(op);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }
}
