package com.girisk.gateway;

import org.springframework.stereotype.Component;

/**
 * 线程级租户上下文（operatorId）。多租户过滤依赖此上下文。
 */
@Component
public class TenantContext {

    private static final ThreadLocal<String> OPERATOR = new ThreadLocal<>();

    public void setOperatorId(String operatorId) {
        OPERATOR.set(operatorId);
    }

    public String getOperatorId() {
        return OPERATOR.get();
    }

    public boolean hasOperator() {
        String id = OPERATOR.get();
        return id != null && !id.isBlank();
    }

    public void clear() {
        OPERATOR.remove();
    }
}
